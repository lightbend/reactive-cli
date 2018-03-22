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

import argonaut.Json
import com.lightbend.rp.reactivecli.annotations.{ Annotations, Module }
import com.lightbend.rp.reactivecli.argonaut.YamlRenderer
import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.kubernetes.{ KubernetesArgs, PodControllerArgs }
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.Config
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.process.jq
import java.io.PrintStream
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scalaz._
import slogging.LazyLogging

import Scalaz._

package object kubernetes extends LazyLogging {
  private[reactivecli] val LivenessInitialDelaySeconds = 60
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
   * This is the main method which generates the Kubernetes resources (PodController/Service).
   */
  def generateResources(dockerImage: String, config: Config, generateDeploymentArgs: GenerateDeploymentArgs, kubernetesArgs: KubernetesArgs): Future[ValidationNel[String, Seq[GeneratedKubernetesResource]]] =
    for {
      namespaceApiVersion <- KubernetesArgs.DefaultNamespaceApiVersion
      appsApiVersion <- kubernetesArgs.podControllerArgs.appsApiVersion
      batchApiVersion <- kubernetesArgs.podControllerArgs.batchApiVersion
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

      val podControllers =
        kubernetesArgs.podControllerArgs.controllerType match {
          case PodControllerArgs.ControllerType.Deployment =>
            Deployment.generate(
              annotations,
              appsApiVersion,
              generateDeploymentArgs.application,
              dockerImage,
              kubernetesArgs.podControllerArgs.imagePullPolicy,
              kubernetesArgs.podControllerArgs.restartPolicy,
              kubernetesArgs.podControllerArgs.numberOfReplicas,
              generateDeploymentArgs.externalServices,
              generateDeploymentArgs.deploymentType,
              kubernetesArgs.transformPodControllers,
              generateDeploymentArgs.akkaClusterJoinExisting)

          case PodControllerArgs.ControllerType.Job =>
            Job.generate(
              annotations,
              batchApiVersion,
              generateDeploymentArgs.application,
              dockerImage,
              kubernetesArgs.podControllerArgs.imagePullPolicy,
              kubernetesArgs.podControllerArgs.restartPolicy,
              kubernetesArgs.podControllerArgs.numberOfReplicas,
              generateDeploymentArgs.externalServices,
              generateDeploymentArgs.deploymentType,
              kubernetesArgs.transformPodControllers,
              generateDeploymentArgs.akkaClusterJoinExisting)
        }

      val services = Service.generate(
        annotations,
        serviceApiVersion,
        kubernetesArgs.serviceArgs.clusterIp,
        generateDeploymentArgs.deploymentType,
        kubernetesArgs.transformServices,
        kubernetesArgs.serviceArgs.loadBalancerIp,
        kubernetesArgs.serviceArgs.serviceType)
      val ingress = Ingress.generate(
        annotations,
        ingressApiVersion,
        if (kubernetesArgs.ingressArgs.hosts.nonEmpty)
          Some(kubernetesArgs.ingressArgs.hosts)
        else
          None,
        kubernetesArgs.ingressArgs.ingressAnnotations,
        kubernetesArgs.transformIngress,
        kubernetesArgs.ingressArgs.name,
        kubernetesArgs.ingressArgs.pathAppend,
        kubernetesArgs.ingressArgs.tlsSecrets)

      val validateAkkaCluster =
        if (annotations.modules.contains(Module.AkkaClusterBootstrapping) && !generateDeploymentArgs.akkaClusterSkipValidation && kubernetesArgs.podControllerArgs.numberOfReplicas < AkkaClusterMinimumReplicas && !generateDeploymentArgs.akkaClusterJoinExisting && kubernetesArgs.generatePodControllers)
          s"Akka Cluster Bootstrapping is enabled so you must specify `--pod-controller-replicas 2` (or greater), or provide `--akka-cluster-join-existing` to only join already formed clusters".failureNel
        else
          ().successNel[String]

      val validateJq =
        if ((kubernetesArgs.transformNamespaces.nonEmpty ||
          kubernetesArgs.transformIngress.nonEmpty ||
          kubernetesArgs.transformServices.nonEmpty || kubernetesArgs.transformPodControllers.nonEmpty) && !jqAvailable)
          "Resources cannot be translated because jq is not installed".failureNel
        else
          ().successNel[String]

      (namespaces |@| podControllers |@| services |@| ingress |@| validateAkkaCluster |@| validateJq) { (ns, pcs, ss, is, _, _) =>
        ns.filter(_ => kubernetesArgs.generateNamespaces).toVector ++
          Seq(pcs).filter(_ => kubernetesArgs.generatePodControllers) ++
          ss.toSeq.filter(_ => kubernetesArgs.generateServices) ++
          is.toSeq.filter(_ => kubernetesArgs.generateIngress)
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

  /**
   * Merges the generated resources. Currently, Ingress annotations are flattened into one.
   * @return
   */
  def mergeGeneratedResources(kubernetesArgs: KubernetesArgs, resources: Seq[GeneratedKubernetesResource]): ValidationNel[String, Seq[GeneratedKubernetesResource]] = {
    val (other, ingress) =
      resources.partition {
        case Ingress(_, _, _, _) => false
        case _ => true
      }

    val ingressTyped = ingress.collect { case i: Ingress => i }

    if (ingressTyped.length < 2)
      resources.successNel
    else if (kubernetesArgs.ingressArgs.name.isEmpty)
      "Ingress resources are being generated by more than one Docker image but no ingress name has been specified. Specify `--ingress-name` flag with a name to use for the Ingress resource"
        .failureNel
    else
      (
        other :+ ingressTyped.tail.foldLeft(ingressTyped.head) {
          case (a, b) =>
            Ingress.merge(kubernetesArgs.ingressArgs.name.get, a, b)
        }).successNel
  }

  private[kubernetes] def format(json: Json) = YamlRenderer.render(json)

  private[kubernetes] def saveToFile(path: String)(generatedResources: Seq[GeneratedKubernetesResource]): Future[Unit] = {
    if (!fileExists(path)) {
      mkDirs(path)
    }

    Future
      .sequence(
        generatedResources.map { r =>
          val fileName = s"${r.resourceType}-${r.name}.yml"
          val file = pathFor(path, fileName)

          r.payload.map { json =>
            val formattedJson = format(json)

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
            val formattedJson = format(r)
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
