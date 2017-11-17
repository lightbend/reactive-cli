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
import com.lightbend.rp.reactivecli.annotations.{ Annotations, Check, CommandCheck, Endpoint, EnvironmentVariable, HttpCheck, LiteralEnvironmentVariable, TcpCheck, Version }

import scala.util.{ Failure, Success, Try }

object Deployment {

  object RpEnvironmentVariables {
    /**
     * Creates pod related environment variables using the Kubernetes Downward API:
     *
     * https://kubernetes.io/docs/tasks/inject-data-application/environment-variable-expose-pod-information/#use-pod-fields-as-values-for-environment-variables
     */
    private val PodEnvs = Map(
      "RP_PLATFORM" -> LiteralEnvironmentVariable("kubernetes"),
      "RP_KUBERNETES_POD_NAME" -> FieldRefEnvironmentVariable("metadata.name"),
      "RP_KUBERNETES_POD_IP" -> FieldRefEnvironmentVariable("status.podIP"))

    /**
     * Generates pod environment variables specific for RP applications.
     */
    def envs(annotations: Annotations): Map[String, EnvironmentVariable] =
      PodEnvs ++
        annotations.version.fold(Map.empty[String, LiteralEnvironmentVariable])(versionEnvs) ++
        endpointEnvs(annotations.endpoints)

    private[kubernetes] def versionEnvs(version: Version): Map[String, LiteralEnvironmentVariable] = {
      Map(
        "RP_VERSION" -> LiteralEnvironmentVariable(version.version),
        "RP_VERSION_MAJOR" -> LiteralEnvironmentVariable(version.major.toString),
        "RP_VERSION_MINOR" -> LiteralEnvironmentVariable(version.minor.toString),
        "RP_VERSION_PATCH" -> LiteralEnvironmentVariable(version.patch.toString)) ++
        version.patchLabel.fold(Map.empty[String, LiteralEnvironmentVariable]) { v =>
          Map("RP_VERSION_PATCH_LABEL" -> LiteralEnvironmentVariable(v))
        }
    }

    private[kubernetes] def endpointEnvs(endpoints: Map[String, Endpoint]): Map[String, EnvironmentVariable] =
      if (endpoints.isEmpty)
        Map(
          "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable("0"))
      else
        Map(
          "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable(endpoints.size.toString),
          "RP_ENDPOINTS" -> LiteralEnvironmentVariable(
            endpoints.values.toList
              .sortBy(_.index)
              .map(v => endpointEnvName(v))
              .mkString(","))) ++
          endpointPortEnvs(endpoints)

    private[kubernetes] def endpointPortEnvs(endpoints: Map[String, Endpoint]): Map[String, EnvironmentVariable] =
      EndpointAutoPort.assignPorts(endpoints)
        .flatMap { assigned =>
          val assignedPortEnv = LiteralEnvironmentVariable(assigned.port.toString)
          val hostEnv = FieldRefEnvironmentVariable("status.podIP")

          Seq(
            s"RP_ENDPOINT_${endpointEnvName(assigned.endpoint)}_HOST" -> hostEnv,
            s"RP_ENDPOINT_${endpointEnvName(assigned.endpoint)}_BIND_HOST" -> hostEnv,
            s"RP_ENDPOINT_${endpointEnvName(assigned.endpoint)}_PORT" -> assignedPortEnv,
            s"RP_ENDPOINT_${endpointEnvName(assigned.endpoint)}_BIND_PORT" -> assignedPortEnv,
            s"RP_ENDPOINT_${assigned.endpoint.index}_HOST" -> hostEnv,
            s"RP_ENDPOINT_${assigned.endpoint.index}_BIND_HOST" -> hostEnv,
            s"RP_ENDPOINT_${assigned.endpoint.index}_PORT" -> assignedPortEnv,
            s"RP_ENDPOINT_${assigned.endpoint.index}_BIND_PORT" -> assignedPortEnv)

        }
        .toMap
  }

  object EndpointAutoPort {
    /**
     * If an endpoint port is undeclared, i.e. `0` it will be assigned a port number starting from this number.
     */
    private val AutoPortNumberStart = 10000

    /**
     * If an endpoint is undeclared, its port number will be of this value.
     */
    private val UndeclaredPortNumber = 0

    /**
     * Represents [[Endpoint]] with its assigned [[port]] number.
     */
    case class Assigned(endpoint: Endpoint, port: Int)

    /**
     * Allocate port number to each endpoint based on:
     * - the endpoint's own declared port number, or,
     * - if the endpoint point is undeclared, it will be obtained based on the incremented value of
     * [[AutoPortNumberStart]].
     */
    def assignPorts(endpoints: Map[String, Endpoint]): Seq[Assigned] =
      endpoints.values.toList
        .foldLeft((AutoPortNumberStart, Seq.empty[Assigned])) { (acc, endpoint) =>
          val (autoPortNumberLast, endpointsAndAssignedPort) = acc

          val (autoPortNumberNext, assignedPort) =
            if (endpoint.port == UndeclaredPortNumber) {
              autoPortNumberLast + 1 -> autoPortNumberLast
            } else {
              autoPortNumberLast -> endpoint.port
            }

          autoPortNumberNext -> (endpointsAndAssignedPort :+ Assigned(endpoint, assignedPort))
        }
        ._2

  }

  /**
   * Represents Kubernetes major and minor version which is required for generating [[Deployment]] resource.
   */
  case class KubernetesVersion(major: Int, minor: Int)

  /**
   * Represents possible values for imagePullPolicy field within the Kubernetes deployment resource.
   */
  object ImagePullPolicy extends Enumeration {
    val Never, IfNotPresent, Always = Value
  }

  val VersionSeparator = "-v"

  implicit def imagePullPolicyEncode = EncodeJson[ImagePullPolicy.Value] {
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
      .toList
      .sortBy(_._1)
      .map {
        case (envName, env) =>
          Json("name" -> envName.asJson).deepmerge(env.asJson)
      }
      .asJson
  }

  implicit def assignedEncode = EncodeJson[EndpointAutoPort.Assigned] { assigned =>
    Json(
      "containerPort" -> assigned.port.asJson,
      "name" -> endpointEnvName(assigned.endpoint).toLowerCase.asJson)
  }

  implicit def endpointsEncode = EncodeJson[Map[String, Endpoint]] { endpoints =>
    EndpointAutoPort.assignPorts(endpoints)
      .toList
      .sortBy(_.endpoint.index)
      .map(_.asJson)
      .asJson
  }

  /**
   * Builds [[Deployment]] resource.
   */
  def generate(annotations: Annotations, kubernetesVersion: KubernetesVersion, imageName: String,
    imagePullPolicy: ImagePullPolicy.Value, noOfReplicas: Int): Try[Deployment] =
    (annotations.appName, annotations.version) match {
      case (Some(appName), Some(version)) =>
        val appVersionMajor = s"$appName$VersionSeparator${version.major}"
        val appVersionMajorMinor = s"$appName$VersionSeparator${version.versionMajorMinor}"
        val appVersion = s"$appName$VersionSeparator${version.version}"
        Success(
          Deployment(
            appVersion,
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
                      "env" -> (annotations.environmentVariables ++ RpEnvironmentVariables.envs(annotations)).asJson,
                      "ports" -> annotations.endpoints.asJson)
                      .deepmerge(annotations.readinessCheck.asJson(readinessProbeEncode))
                      .deepmerge(annotations.healthCheck.asJson(livenessProbeEncode))).asJson)))))
      case _ =>
        Failure(new IllegalArgumentException("Unable to generate Kubernetes Deployment: both application name and version are required"))
    }

  private[kubernetes] def apiVersion(kubernetesVersion: KubernetesVersion): String = {
    val version = (kubernetesVersion.major, kubernetesVersion.minor)
    val kubernetes18 = (1, 8)
    val versionCompare = Seq(version, kubernetes18).sorted

    if (versionCompare.head == kubernetes18)
      "apps/v1beta2"
    else
      "apps/v1beta1"
  }

}

/**
 * Represents the generated Kubernetes deployment resource.
 */
case class Deployment(name: String, payload: Json) extends GeneratedKubernetesResource {
  val resourceType = "deployment"
}