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

package com.lightbend.rp.reactivecli.argparse

import scala.collection.immutable.Seq
import GenerateDeploymentArgs.{ DockerRegistryUseHttpsDefault, DockerRegistryValidateTlsDefault, DockerRegistryUseLocalDefault }

/**
 * Base type which represents input argument for a specific command invoked by the user.
 */
sealed trait CommandArgs

object VersionArgs extends CommandArgs

object GenerateDeploymentArgs {
  val DockerRegistryUseHttpsDefault = true
  val DockerRegistryValidateTlsDefault = true
  val DockerRegistryUseLocalDefault = false

  /**
   * Convenience method to set the [[GenerateDeploymentArgs]] values when parsing the complete user input.
   * Refer to [[InputArgs.parser()]] for more details.
   */
  def set[T](f: (T, GenerateDeploymentArgs) => GenerateDeploymentArgs): (T, InputArgs) => InputArgs =
    (value, args) => {
      args.commandArgs match {
        case Some(v: GenerateDeploymentArgs) =>
          val updates = f.apply(value, v)
          args.copy(commandArgs = Some(updates))

        case _ => args
      }
    }
}

object DeploymentType {
  val Canary = "canary"
  val BlueGreen = "blue-green"
  val Rolling = "rolling"
  val All = Seq(Canary, BlueGreen, Rolling)
}

sealed trait DeploymentType

case object CanaryDeploymentType extends DeploymentType
case object BlueGreenDeploymentType extends DeploymentType
case object RollingDeploymentType extends DeploymentType

/**
 * Represents the discovery method during Akka Boostrap on Kubernetes.
 */
sealed trait DiscoveryMethod
object DiscoveryMethod {
  case object KubernetesApi extends DiscoveryMethod {
    override def toString: String = "kubernetes-api"
  }
  case object AkkaDns extends DiscoveryMethod {
    override def toString = "akka-dns"
  }
  def all = Seq(AkkaDns, KubernetesApi)
}

/**
 * Represents the input argument for `generate-deployment` command.
 */
case class GenerateDeploymentArgs(
  application: Option[String] = None,
  akkaClusterJoinExisting: Boolean = false,
  akkaClusterSkipValidation: Boolean = false,
  deploymentType: DeploymentType = CanaryDeploymentType,
  discoveryMethod: DiscoveryMethod = DiscoveryMethod.KubernetesApi,
  dockerImages: Seq[String] = Seq.empty,
  name: Option[String] = None,
  version: Option[String] = None,
  environmentVariables: Map[String, String] = Map.empty,
  cpu: Option[Double] = None,
  memory: Option[Long] = None,
  diskSpace: Option[Long] = None,
  targetRuntimeArgs: Option[TargetRuntimeArgs] = None,
  registryUsername: Option[String] = None,
  registryPassword: Option[String] = None,
  registryUseHttps: Boolean = DockerRegistryUseHttpsDefault,
  registryValidateTls: Boolean = DockerRegistryValidateTlsDefault,
  registryUseLocal: Boolean = DockerRegistryUseLocalDefault,
  externalServices: Map[String, Seq[String]] = Map.empty) extends CommandArgs
