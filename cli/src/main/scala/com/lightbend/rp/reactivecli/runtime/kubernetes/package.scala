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

import java.io.PrintStream
import java.nio.file.{ Files, Path }

import argonaut.PrettyParams
import com.lightbend.rp.reactivecli.annotations.{ Annotations, Endpoint }
import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.kubernetes.KubernetesArgs
import com.lightbend.rp.reactivecli.Done
import com.lightbend.rp.reactivecli.docker.Config
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment.VersionSeparator
import slogging.LazyLogging

import scala.util.{ Failure, Success, Try }

package object kubernetes extends LazyLogging {
  /**
   * Valid characters for endpoint name.
   * The declared endpoint name will be made uppercase, and all characters outside the valid chars range will be
   * swapped with '_'.
   */
  private val ValidEndpointChars =
    (('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z') ++ Seq('_', '-')).toSet

  private val ValidEndpointServiceChars =
    ValidEndpointChars - '_'

  /**
   * This is the main method which generates the kubernetes resources.
   *
   * This method accepts a docker config supplied by `getDockerConfig`, generates the Kubernetes resources, and
   * supplies these generated resources to the `outputHandler`.
   *
   * The `getDockerConfig` and `outputHandler` are supplied as function, and when `generateDeploymentArgs` and
   * `kubernetesArgs` are supplied, the function is invoked and the resulting [[Try[Done]] is returned.
   */
  def generateResources(getDockerConfig: String => Try[Config], outputHandler: Seq[GeneratedKubernetesResource] => Unit)(generateDeploymentArgs: GenerateDeploymentArgs, kubernetesArgs: KubernetesArgs): Try[Done] = {

    def getLabel(config: Config): Try[Map[String, String]] =
      config.config.Labels match {
        case Some(v) if v.nonEmpty => Success(v)
        case _ => Failure(new IllegalArgumentException("Unable to generate Kubernetes resources from empty labels."))
      }

    for {
      config <- getDockerConfig(generateDeploymentArgs.dockerImage.get)

      label <- getLabel(config)

      annotations = Annotations(label, generateDeploymentArgs)

      namespace <- Namespace.generate(annotations)

      deployment <- Deployment.generate(
        annotations,
        kubernetesArgs.kubernetesVersion.get,
        generateDeploymentArgs.dockerImage.get,
        kubernetesArgs.deploymentArgs.imagePullPolicy,
        kubernetesArgs.deploymentArgs.numberOfReplicas)

      service <- Service.generate(annotations, kubernetesArgs.serviceArgs.clusterIp)

      ingress <- Ingress.generate(
        annotations,
        kubernetesArgs.ingressArgs.ingressAnnotations,
        kubernetesArgs.ingressArgs.pathAppend)
    } yield {
      outputHandler(namespace.toSeq ++ Seq(deployment, service, ingress))
      Done
    }
  }

  /**
   * Accepts the instructions supplied by the `output`, and returns the appropriate function to handle the
   * generated Kubernetes resources.
   */
  def handleGeneratedResources(output: KubernetesArgs.Output): Seq[GeneratedKubernetesResource] => Unit =
    output match {
      case KubernetesArgs.Output.PipeToKubeCtl(out) => pipeToStream(out)
      case KubernetesArgs.Output.SaveToFile(path) => saveToFile(path)
    }

  private[kubernetes] def saveToFile(path: Path)(generatedResources: Seq[GeneratedKubernetesResource]): Unit = {
    logger.debug(s"Saving to ${path.toAbsolutePath}")
    if (!Files.exists(path)) {
      Files.createDirectories(path)
    }

    generatedResources.foreach { r =>
      val fileName = s"${r.resourceType}-${r.name}.json"
      val file = path.resolve(fileName)
      val formattedJson = r.payload.pretty(PrettyParams.spaces2.copy(preserveOrder = true))

      logger.debug(fileName)
      Files.deleteIfExists(file)
      Files.write(file, formattedJson.getBytes)
    }

    logger.debug("Done!")
  }

  private[kubernetes] def pipeToStream(out: PrintStream)(generatedResources: Seq[GeneratedKubernetesResource]): Unit =
    generatedResources.foreach { r =>
      val formattedJson = r.payload.pretty(PrettyParams.spaces2.copy(preserveOrder = true))
      out.println("---")
      out.println(formattedJson)
    }

  private[kubernetes] def endpointName(endpoint: Endpoint): String =
    endpoint.version.fold(endpoint.name)(v => s"${endpoint.name}$VersionSeparator$v")

  private[kubernetes] def endpointServiceName(endpoint: Endpoint): String =
    endpointName(endpoint)
      .map(c => if (ValidEndpointServiceChars.contains(c)) c else '-')
      .toLowerCase

  private[kubernetes] def endpointEnvName(endpoint: Endpoint): String =
    endpointName(endpoint)
      .map(c => if (ValidEndpointChars.contains(c)) c else '_')
      .toUpperCase

  private[kubernetes] def serviceName(annotations: Annotations): Option[String] =
    annotations.appName
}
