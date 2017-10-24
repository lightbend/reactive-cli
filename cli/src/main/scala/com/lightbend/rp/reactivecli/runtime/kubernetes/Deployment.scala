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

package com.lightbend.rp.reactivecli.runtime.kubernetes

import argonaut._
import Argonaut._
import com.lightbend.rp.reactivecli.annotations.kubernetes.{ ConfigMapEnvironmentVariable, FieldRefEnvironmentVariable }
import com.lightbend.rp.reactivecli.annotations.{ Annotations, Check, CommandCheck, Endpoint, EnvironmentVariable, HttpCheck, HttpEndpoint, LiteralEnvironmentVariable, TcpCheck, TcpEndpoint, UdpEndpoint }

import scala.util.{ Failure, Success, Try }

object Deployment {
  type KubernetesMajorVersion = Int
  type KubernetesMinorVersion = Int

  object ImagePullPolicy {
    case object Never extends ImagePullPolicy
    case object IfNotPresent extends ImagePullPolicy
    case object Always extends ImagePullPolicy
  }
  sealed trait ImagePullPolicy

  val VersionSeparator = "-v"

  implicit def imagePullPolicyEncode = EncodeJson[ImagePullPolicy] {
    case ImagePullPolicy.Never => "Never".asJson
    case ImagePullPolicy.IfNotPresent => "IfNotPresent".asJson
    case ImagePullPolicy.Always => "Always".asJson
  }

  implicit def checkPortNumberEncode = EncodeJson[Check.PortNumber](_.value.asJson)

  implicit def checkServiceNumberEncode = EncodeJson[Check.ServiceName](_.value.asJson)

  implicit def checkPortEncode = EncodeJson[Check.Port] {
    case v: Check.PortNumber => v.asJson
    case v: Check.ServiceName => v.asJson
  }

  implicit def commandCheckEncode = EncodeJson[CommandCheck] { check =>
    Json(
      "exec" -> Json(
        "command" -> check.command.toList.asJson))
  }

  implicit def tcpCheckEncode = EncodeJson[TcpCheck] { check =>
    Json(
      "tcpSocket" -> Json(
        "port" -> check.port.asJson),
      "periodSeconds" -> check.intervalSeconds.asJson)
  }

  implicit def httpCheckEncode = EncodeJson[HttpCheck] { check =>
    Json(
      "httpGet" -> Json(
        "path" -> check.path.asJson,
        "port" -> check.port.asJson),
      "periodSeconds" -> check.intervalSeconds.asJson)
  }

  implicit def checkEncode = EncodeJson[Check] {
    case v: CommandCheck => v.asJson
    case v: TcpCheck => v.asJson
    case v: HttpCheck => v.asJson
  }

  def readinessProbeEncode = EncodeJson[Option[Check]] {
    case Some(check) => Json("readinessProbe" -> check.asJson)
    case _ => jEmptyObject
  }

  def livenessProbeEncode = EncodeJson[Option[Check]] {
    case Some(check) => Json("livenessProbe" -> check.asJson)
    case _ => jEmptyObject
  }

  implicit def literalEnvironmentVariableEncode = EncodeJson[LiteralEnvironmentVariable] { env =>
    Json("value" -> env.value.asJson)
  }

  implicit def fieldRefEnvironmentVariableEncode = EncodeJson[FieldRefEnvironmentVariable] { env =>
    Json(
      "valueFrom" -> Json(
        "fieldRef" -> Json(
          "fieldPath" -> env.fieldPath.asJson)))
  }

  implicit def configMapEnvironmentVariableEncode = EncodeJson[ConfigMapEnvironmentVariable] { env =>
    Json(
      "valueFrom" -> Json(
        "configMapKeyRef" -> Json(
          "name" -> env.mapName.asJson,
          "key" -> env.key.asJson)))
  }

  implicit def environmentVariableEncode = EncodeJson[EnvironmentVariable] {
    case v: LiteralEnvironmentVariable => v.asJson
    case v: FieldRefEnvironmentVariable => v.asJson
    case v: ConfigMapEnvironmentVariable => v.asJson
  }

  implicit def environmentVariablesEncode = EncodeJson[Map[String, EnvironmentVariable]] { envs =>
    envs
      .map {
        case (envName, env) =>
          Json("name" -> envName.asJson).deepmerge(env.asJson)
      }
      .toList
      .asJson
  }

  implicit def httpEndpointEncode = EncodeJson[HttpEndpoint](endpointToJson)
  implicit def tcpEndpointEncode = EncodeJson[TcpEndpoint](endpointToJson)
  implicit def udpEndpointEncode = EncodeJson[UdpEndpoint](endpointToJson)

  implicit def endpointEncode = EncodeJson[Endpoint] {
    case v: HttpEndpoint => v.asJson
    case v: TcpEndpoint => v.asJson
    case v: UdpEndpoint => v.asJson
  }

  implicit def endpointsEncode = EncodeJson[Map[String, Endpoint]] { endpoints =>
    endpoints
      .map(_._2.asJson)
      .toList
      .asJson
  }

  private def endpointToJson(endpoint: Endpoint): Json =
    Json(
      "containerPort" -> endpoint.port.asJson,
      "name" -> endpointName(endpoint).asJson)

  def generate(annotations: Annotations, kubernetesVersion: (KubernetesMajorVersion, KubernetesMinorVersion),
    imageName: String, imagePullPolicy: ImagePullPolicy, noOfReplicas: Int): Try[Json] =
    (annotations.appName, annotations.version) match {
      case (Some(appName), Some(version)) =>
        val appVersionMajor = s"$appName$VersionSeparator${version.major}"
        val appVersionMajorMinor = s"$appName$VersionSeparator${version.versionMajorMinor}"
        val appVersion = s"$appName$VersionSeparator${version.version}"
        Success(
          Json(
            "apiVersion" -> apiVersion(kubernetesVersion).asJson,
            "kind" -> "Deployment".asJson,
            "metadata" -> Json(
              "name" -> appVersion.asJson,
              "labels" -> Json(
                "app" -> appName.asJson,
                "appVersionMajor" -> appVersionMajor.asJson,
                "appVersionMajorMinor" -> appVersionMajorMinor.asJson,
                "appVersion" -> appVersion.asJson)),
            "spec" -> Json(
              "replicas" -> noOfReplicas.asJson,
              "serviceName" -> appVersionMajor.asJson,
              "template" -> Json(
                "app" -> appName.asJson,
                "appVersionMajor" -> appVersionMajor.asJson,
                "appVersionMajorMinor" -> appVersionMajorMinor.asJson,
                "appVersion" -> appVersion.asJson),
              "spec" -> Json(
                "containers" -> List(
                  Json(
                    "name" -> appName.asJson,
                    "image" -> imageName.asJson,
                    "imagePullPolicy" -> imagePullPolicy.asJson,
                    "env" -> annotations.environmentVariables.asJson,
                    "ports" -> annotations.endpoints.asJson)
                    .deepmerge(annotations.readinessCheck.asJson(readinessProbeEncode))
                    .deepmerge(annotations.healthCheck.asJson(livenessProbeEncode))).asJson))))
      case _ =>
        Failure(new IllegalArgumentException("Unable to generate Kubernetes Deployment: both application name and version are required"))
    }

  private[kubernetes] def apiVersion(kubernetesVersion: (KubernetesMajorVersion, KubernetesMinorVersion)): String = {
    val (major, minor) = kubernetesVersion
    if (major >= 1 && minor >= 8)
      "apps/v1beta2"
    else
      "apps/v1beta1"
  }

}

