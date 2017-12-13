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

package com.lightbend.rp.reactivecli.argparse.kubernetes

import com.lightbend.rp.reactivecli.argparse.{ GenerateDeploymentArgs, InputArgs, TargetRuntimeArgs }
import com.lightbend.rp.reactivecli.process.kubectl
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment
import java.io.PrintStream
import java.nio.file.Path

object KubernetesArgs {
  object Output {
    /**
     * Represents user input to save generated resources into the directory specified by [[dir]].
     */
    case class SaveToFile(dir: Path) extends Output

    /**
     * Represents user input to pipe the generated resources into the stream specified by [[out]].
     * The generated resources will be formatted in the format acceptable to `kubectl` command.
     */
    case class PipeToStream(out: PrintStream) extends Output
  }

  /**
   * Base trait which indicates the output required for generated kubernetes resources.
   */
  sealed trait Output

  val DefaultNumberOfReplicas: Int = 1
  val DefaultImagePullPolicy: Deployment.ImagePullPolicy.Value = Deployment.ImagePullPolicy.IfNotPresent

  lazy val DefaultNamespaceApiVersion: String = kubectl.findApi("v1")
  lazy val DefaultIngressApiVersion: String = kubectl.findApi("extensions/v1beta1")
  lazy val DefaultPodControllerApiVersion: String = kubectl.findApi("apps/v1beta2", "apps/v1beta1")
  lazy val DefaultServiceApiVersion: String = kubectl.findApi("v1")

  /**
   * Convenience method to set the [[KubernetesArgs]] values when parsing the complete user input.
   * Refer to [[InputArgs.parser()]] for more details.
   */
  def set[T](f: (T, KubernetesArgs) => KubernetesArgs): (T, InputArgs) => InputArgs = { (val1: T, inputArgs: InputArgs) =>
    GenerateDeploymentArgs
      .set { (val2: T, deploymentArgs) =>
        deploymentArgs.targetRuntimeArgs match {
          case Some(v: KubernetesArgs) =>
            deploymentArgs.copy(targetRuntimeArgs = Some(f(val2, v)))
          case _ => deploymentArgs
        }
      }
      .apply(val1, inputArgs)

  }
}

/**
 * Represents user input arguments required to build Kubernetes specific resources.
 */
case class KubernetesArgs(
  generateIngress: Boolean = false,
  generateNamespaces: Boolean = false,
  generatePodControllers: Boolean = false,
  generateServices: Boolean = false,
  transformIngress: Option[String] = None,
  transformNamespaces: Option[String] = None,
  transformPodControllers: Option[String] = None,
  transformServices: Option[String] = None,
  namespace: Option[String] = None,
  podControllerArgs: PodControllerArgs = PodControllerArgs(),
  serviceArgs: ServiceArgs = ServiceArgs(),
  ingressArgs: IngressArgs = IngressArgs(),
  output: KubernetesArgs.Output = KubernetesArgs.Output.PipeToStream(System.out)) extends TargetRuntimeArgs {
  def generateAll: Boolean =
    !generateIngress && !generatePodControllers && !generateServices

  def shouldGenerateIngress: Boolean =
    generateIngress || generateAll

  def shouldGenerateNamespaces: Boolean =
    generateNamespaces

  def shouldGeneratePodControllers: Boolean =
    generatePodControllers || generateAll

  def shouldGenerateServices: Boolean =
    generateServices || generateAll
}
