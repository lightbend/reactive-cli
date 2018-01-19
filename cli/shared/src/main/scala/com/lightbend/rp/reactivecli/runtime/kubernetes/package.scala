/*
 * Copyright 2017 Lightbend, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.rp.reactivecli.runtime

import argonaut.PrettyParams
import com.lightbend.rp.reactivecli.annotations.{ Annotations, Module }
import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.kubernetes.KubernetesArgs
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.Config
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.process.jq
import java.io.PrintStream
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import scalaz._
import slogging.LazyLogging

import Scalaz._

package object kubernetes extends LazyLogging {
  private[reactivecli] val AkkaClusterMinimumReplicas = 2
  private[reactivecli] val StatusPeriodSeconds = 10

  /**
   * Valid characters for endpoint environment variable name.
   * The declared endpoint name will be made uppercase, and all characters outside the valid chars range will be
   * swapped with '_'.
   */
  private val ValidEndpointChars =
    (('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z') ++ Seq('_')).toSet

  /**
   * Valid characters for endpoint service name. The name will be made lowercase, and all characters outside the
   * valid chars range with be swapped with '-'.
   */
  private val ValidEndpointServiceChars =
    ValidEndpointChars - '_' + '-'

  /**
   * These characters will be trimmed from both sides of the string.
   */
  private val EndpointTrimChars = Set('_', '-')

  /**
   * This is the main method which generates the Kubernetes resources.
   */
  def generateResources(config: Config, generateDeploymentArgs: GenerateDeploymentArgs, kubernetesArgs: KubernetesArgs): Future[ValidationNel[String, Seq[GeneratedKubernetesResource]]] =
    for {
      namespaceApiVersion <- KubernetesArgs.DefaultNamespaceApiVersion
      podControllerApiVersion <- kubernetesArgs.podControllerArgs.apiVersion
      serviceApiVersion <- kubernetesArgs.serviceArgs.apiVersion
      ingressApiVersion <- kubernetesArgs.ingressArgs.apiVersion
      jqAvailable <- jq.available
    } yield {
      val annotations = Annotations(
        config.config.Labels.getOrElse(Map.empty),
        generateDeploymentArgs)
      val namespaces = Namespace.generate(
        annotations,
        namespaceApiVersion,
        kubernetesArgs.transformNamespaces)
      val deployments = Deployment.generate(
        annotations,
        podControllerApiVersion,
        generateDeploymentArgs.dockerImage.get,
        kubernetesArgs.podControllerArgs.imagePullPolicy,
        kubernetesArgs.podControllerArgs.numberOfReplicas,
        generateDeploymentArgs.externalServices,
        generateDeploymentArgs.deploymentType,
        kubernetesArgs.transformPodControllers,
        generateDeploymentArgs.joinExistingAkkaCluster)
      val services = Service.generate(
        annotations,
        serviceApiVersion,
        kubernetesArgs.serviceArgs.clusterIp,
        generateDeploymentArgs.deploymentType,
        kubernetesArgs.transformServices)
      val ingress = Ingress.generate(
        annotations,
        ingressApiVersion,
        kubernetesArgs.ingressArgs.ingressAnnotations,
        kubernetesArgs.ingressArgs.pathAppend,
        kubernetesArgs.transformIngress)

      val validateAkkaCluster =
        if (annotations.modules.contains(Module.AkkaClusterBootstrapping) && kubernetesArgs.podControllerArgs.numberOfReplicas < AkkaClusterMinimumReplicas && !generateDeploymentArgs.joinExistingAkkaCluster)
          s"Akka Cluster Bootstrapping is enabled so you must specify `--pod-controller-replicas 2` (or greater), or provide `--join-existing-akka-cluster` to only join already formed clusters".failureNel
        else
          ().successNel[String]

      val validateJq =
        if ((kubernetesArgs.transformNamespaces.nonEmpty ||
          kubernetesArgs.transformIngress.nonEmpty ||
          kubernetesArgs.transformServices.nonEmpty || kubernetesArgs.transformPodControllers.nonEmpty) && !jqAvailable)
          "Resources cannot be translated because jq is not installed".failureNel
        else
          ().successNel[String]

      (namespaces |@| deployments |@| services |@| ingress |@| validateAkkaCluster |@| validateJq) { (ns, ds, ss, is, _, _) =>
        ns.filter(_ => kubernetesArgs.shouldGenerateNamespaces).toSeq ++
          Seq(ds).filter(_ => kubernetesArgs.shouldGeneratePodControllers) ++
          ss.toSeq.filter(_ => kubernetesArgs.shouldGenerateServices) ++
          is.toSeq.filter(_ => kubernetesArgs.shouldGenerateIngress)
      }
    }

  /**
   * Accepts the instructions supplied by the `output`, and returns the appropriate function to handle the
   * generated Kubernetes resources.
   */
  def handleGeneratedResources(output: KubernetesArgs.Output): Seq[GeneratedKubernetesResource] => Future[Unit] =
    output match {
      case KubernetesArgs.Output.PipeToStream(out) => pipeToStream(out)
      case KubernetesArgs.Output.SaveToFile(path) => saveToFile(path)
    }

  private[kubernetes] def saveToFile(path: String)(generatedResources: Seq[GeneratedKubernetesResource]): Future[Unit] = {
    if (!fileExists(path)) {
      mkDirs(path)
    }

    Future
      .sequence(
        generatedResources.map { r =>
          val fileName = s"${r.resourceType}-${r.name}.json"
          val file = pathFor(path, fileName)

          r.payload.map { json =>
            val formattedJson = json.spaces2

            logger.debug(fileName)

            if (fileExists(file)) {
              deleteFile(file)
            }

            writeFile(file, formattedJson)
          }
        })
      .map(_ => ())
  }

  private[kubernetes] def pipeToStream(out: PrintStream)(generatedResources: Seq[GeneratedKubernetesResource]): Future[Unit] = {
    val future =
      Future
        .sequence(generatedResources.map(_.payload))
        .map {
          _.foreach { r =>
            val formattedJson = r.spaces2
            out.println("---")
            out.println(formattedJson)
          }
        }

    future.onFailure {
      case t: Throwable =>
        logger.debug("Exception thrown when piping resources to stream", t)
    }

    future
  }

  private[kubernetes] def envVarName(name: String): String =
    name
      .map(c => if (ValidEndpointChars.contains(c)) c else '_')
      .dropWhile(EndpointTrimChars.contains)
      .reverse
      .dropWhile(EndpointTrimChars.contains)
      .reverse
      .toUpperCase

  private[kubernetes] def serviceName(name: String, additionalChars: Set[Char] = Set.empty): String =
    name
      .map(c => if (ValidEndpointServiceChars.contains(c) || additionalChars.contains(c)) c else '-')
      .dropWhile(EndpointTrimChars.contains)
      .reverse
      .dropWhile(EndpointTrimChars.contains)
      .reverse
      .toLowerCase
}
