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

import argonaut._
import com.lightbend.rp.reactivecli.annotations._
import com.lightbend.rp.reactivecli.argparse._
import com.lightbend.rp.reactivecli.argparse.marathon._
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.Config
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.process.jq
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scalaz._

import Argonaut._
import Scalaz._

package object marathon {
  private[reactivecli] val HealthGracePeriodSeconds = 60
  private[reactivecli] val HealthIntervalSeconds = 60
  private[reactivecli] val StatusGracePeriodSeconds = 60
  private[reactivecli] val StatusIntervalSeconds = 15

  def generateConfiguration(dockerImagesConfigs: Seq[(String, Config)], generateDeploymentArgs: GenerateDeploymentArgs, marathonArgs: MarathonArgs): Future[ValidationNel[String, GeneratedMarathonConfiguration]] =
    for {
      jqAvailable <- jq.available
    } yield {
      // NOTE: annotations can have a namespace, but it is unused here. It's also unused on Kubernetes. It should probably
      // be removed from the annotations

      val marathonEntriesValidation =
        dockerImagesConfigs
          .map {
            case (image, config) =>
              val annotations = Annotations(
                config.config.Labels.getOrElse(Map.empty),
                generateDeploymentArgs)

              def validateAkkaCluster =
                if (annotations.modules.contains(Module.AkkaClusterBootstrapping) && !generateDeploymentArgs.akkaClusterSkipValidation && marathonArgs.instances < AkkaClusterMinimumReplicas && !generateDeploymentArgs.akkaClusterJoinExisting)
                  s"Akka Cluster Bootstrapping is enabled so you must specify `--pod-controller-replicas 2` (or greater), or provide `--akka-cluster-join-existing` to only join already formed clusters".failureNel
                else
                  ().successNel[String]

              (
                annotations.applicationValidation(generateDeploymentArgs.application) |@|
                annotations.appNameValidation |@|
                annotations.versionValidation |@|
                validateAkkaCluster) { (applicationArgs, rawAppName, version, _) =>
                  val appName = serviceName(rawAppName)
                  val appNameVersion = serviceName(s"$appName$VersionSeparator$version")
                  val (configId, serviceResourceName) =
                    generateDeploymentArgs.deploymentType match {
                      case CanaryDeploymentType => appNameVersion -> appName
                      case BlueGreenDeploymentType => appNameVersion -> appNameVersion
                      case RollingDeploymentType => appName -> appName
                    }

                  val appId = marathonArgs.namespace.fold(s"/$configId")(ns => s"/$ns/$configId")

                  val labels = List(
                    "APP_NAME" -> jString(appName),
                    "APP_NAME_VERSION" -> jString(appNameVersion)) ++
                    annotations.akkaClusterBootstrapSystemName.fold(List.empty[(String, Json)])(system => List("ACTOR_SYSTEM_NAME" -> jString(system)))

                  val enableChecks =
                    annotations.modules.contains(Module.Status) && annotations.modules.contains(Module.AkkaManagement)

                  val healthChecks =
                    if (enableChecks)
                      jObjectFields("healthChecks" -> jArrayElements(
                        jObjectFields(
                          "path" -> jString("/platform-tooling/healthy"),
                          "portName" -> jString(portName(AkkaManagementPortName)),
                          "protocol" -> jString("HTTP"),
                          "gracePeriodSeconds" -> jNumber(HealthGracePeriodSeconds),
                          "intervalSeconds" -> jNumber(HealthIntervalSeconds))))
                    else
                      jEmptyObject

                  val readinessChecks =
                    if (enableChecks)
                      jObjectFields("readinessChecks" -> jArrayElements(
                        jObjectFields(
                          "path" -> jString("/platform-tooling/ready"),
                          "portName" -> jString(portName(AkkaManagementPortName)),
                          "protocol" -> jString("HTTP"),
                          "gracePeriodSeconds" -> jNumber(StatusGracePeriodSeconds),
                          "intervalSeconds" -> jNumber(StatusIntervalSeconds))))
                    else
                      jEmptyObject

                  val commandArgs =
                    applicationArgs match {
                      case None =>
                        jEmptyObject

                      case Some(c) =>
                        if (c.isEmpty)
                          jEmptyObject
                        else
                          Json("args" -> jArray(c.map(jString).toList))
                    }

                  jObjectFields(
                    "id" -> jString(appId),
                    "container" -> jObjectFields(
                      "docker" -> jObjectFields(
                        "image" -> jString(image),
                        "forcePullImage" -> jBool(marathonArgs.registryForcePull),
                        "network" -> jString("BRIDGE"),
                        "portMappings" -> jArray(
                          annotations
                            .endpoints
                            .values
                            .toList
                            .sortBy(_.index)
                            .flatMap {
                              case HttpEndpoint(_, name, port, ingress) =>
                                Some(
                                  jObjectFields(
                                    "containerPort" -> jNumber(port),
                                    "servicePort" -> jNumber(port),
                                    "protocol" -> jString("tcp"),
                                    "name" -> jString(portName(name))))
                              case TcpEndpoint(_, name, port) =>
                                Some(
                                  jObjectFields(
                                    "containerPort" -> jNumber(port),
                                    "servicePort" -> jNumber(port),
                                    "protocol" -> jString("tcp"),
                                    "name" -> jString(portName(name))))
                              case UdpEndpoint(_, name, port) =>
                                Some(
                                  jObjectFields(
                                    "containerPort" -> jNumber(port),
                                    "servicePort" -> jNumber(port),
                                    "protocol" -> jString("udp"),
                                    "name" -> jString(portName(name))))
                            }))),
                    "instances" -> jNumber(marathonArgs.instances),
                    "upgradeStrategy" -> jObjectFields(
                      "maximumOverCapacity" -> jNumber(0),
                      "minimumHealthCapacity" -> jNumber(0.5)
                    ),
                    "killSelection" -> jString("YOUNGEST_FIRST"),
                    "env" -> jObjectAssocList(
                      (
                        annotations
                          .environmentVariables
                          .collect {
                            case (k, LiteralEnvironmentVariable(v)) => k -> v
                          }

                          ++
                        RpEnvironmentVariables
                          .envs(marathonArgs.namespace, annotations, serviceResourceName, marathonArgs.instances, generateDeploymentArgs.externalServices, generateDeploymentArgs.akkaClusterJoinExisting))
                        .toList
                        .map { case (k, v) => k -> jString(v) }),
                    "labels" -> jObjectAssocList(labels))
                    .deepmerge(commandArgs)
                    .deepmerge(readinessChecks)
                    .deepmerge(healthChecks)
                    .deepmerge(annotations.memory.fold(jEmptyObject)(m => jObjectFields("mem" -> jNumber(m / 1024 / 1024))))
                    .deepmerge(annotations.cpu.fold(jEmptyObject)(c => jObjectFields("cpus" -> jNumber(c))))
                }
          }
          .foldLeft(Seq.empty[Json].successNel[String]) {
            case (acc, v) => (acc |@| v)(_ :+ _)
          }

      val validateGroup =
        if (marathonArgs.namespace.isEmpty && dockerImagesConfigs.length > 1)
          "Missing group name; provide via `--namespace` flag".failureNel
        else
          ().successNel[String]

      val validateJq =
        if (marathonArgs.transformOutput.nonEmpty && !jqAvailable)
          "Resources cannot be translated because jq is not installed".failureNel
        else
          ().successNel[String]

      (marathonEntriesValidation |@| validateGroup |@| validateJq) { (marathonEntries, _, _) =>
        val jsonConfig =
          if (marathonEntries.length > 1)
            jObjectFields("apps" -> jArrayElements(marathonEntries: _*))
              .deepmerge(marathonArgs.namespace.fold(jEmptyObject)(ns => jObjectFields("id" -> jString(s"/$ns"))))
          else if (marathonEntries.length > 0)
            marathonEntries.head
          else
            jEmptyObject

        GeneratedMarathonConfiguration("", "", Future.successful(jsonConfig))
      }
    }

  def outputConfiguration(config: GeneratedMarathonConfiguration, output: MarathonArgs.Output): Future[Unit] =
    config.payload.map { json =>
      val data = json.spaces4

      output match {
        case MarathonArgs.Output.PipeToStream(out) =>
          out.println(data)
        case MarathonArgs.Output.SaveToFile(path) =>
          mkDirs(parentFor(path))

          if (fileExists(path)) {
            deleteFile(path)
          }

          writeFile(path, data)
      }
    }

  private[marathon] def envVarName(name: String): String =
    name
      .map(c => if (ValidEndpointChars.contains(c)) c else '_')
      .dropWhile(EndpointTrimChars.contains)
      .reverse
      .dropWhile(EndpointTrimChars.contains)
      .reverse
      .toUpperCase

  private[marathon] def serviceName(name: String, additionalChars: Set[Char] = Set.empty): String =
    name
      .map(c => if (ValidEndpointServiceChars.contains(c) || additionalChars.contains(c)) c else '-')
      .dropWhile(EndpointTrimChars.contains)
      .reverse
      .dropWhile(EndpointTrimChars.contains)
      .reverse
      .toLowerCase

  private[marathon] def portName(name: String): String =
    name.filter(_.isLetter)

  private[marathon] def portEnvName(name: String): String =
    portName(name).toUpperCase

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

  private val VersionSeparator = "-v"

  /**
   * These characters will be trimmed from both sides of the string.
   */
  private val EndpointTrimChars = Set('_', '-')
}
