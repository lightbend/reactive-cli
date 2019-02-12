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
package kubernetes

import argonaut._
import com.lightbend.rp.reactivecli.annotations.kubernetes.{ ConfigMapEnvironmentVariable, FieldRefEnvironmentVariable, SecretKeyRefEnvironmentVariable }
import com.lightbend.rp.reactivecli.annotations._
import com.lightbend.rp.reactivecli.argparse._
import com.lightbend.rp.reactivecli.runtime._
import scala.collection.immutable.Seq
import scalaz._

import Argonaut._
import Scalaz._

object PodTemplate {

  object RpEnvironmentVariables {
    /**
     * Environment variables in this set will be space-concatenated when the various environment variable
     * maps are merged.
     */
    private val ConcatLiteralEnvs = Set("RP_JAVA_OPTS")

    /**
     * Creates pod related environment variables using the Kubernetes Downward API:
     *
     * https://kubernetes.io/docs/tasks/inject-data-application/environment-variable-expose-pod-information/#use-pod-fields-as-values-for-environment-variables
     */
    private val PodEnvs = Map(
      "RP_PLATFORM" -> LiteralEnvironmentVariable("kubernetes"),
      "RP_KUBERNETES_POD_NAME" -> FieldRefEnvironmentVariable("metadata.name"),
      "RP_KUBERNETES_POD_IP" -> FieldRefEnvironmentVariable("status.podIP"),
      "RP_NAMESPACE" -> FieldRefEnvironmentVariable("metadata.namespace"))

    /**
     * Generates pod environment variables specific for RP applications.
     */
    def envs(annotations: Annotations, serviceResourceName: String, noOfReplicas: Int, externalServices: Map[String, Seq[String]], akkaClusterJoinExisting: Boolean, discoveryMethod: DiscoveryMethod): Map[String, EnvironmentVariable] =
      mergeEnvs(
        PodEnvs,
        appNameEnvs(annotations.appName),
        annotations.version.fold(Map.empty[String, EnvironmentVariable])(versionEnvs),
        appTypeEnvs(annotations.appType, annotations.modules),
        Map("RP_JAVA_OPTS" -> LiteralEnvironmentVariable(playPidDevNull)),
        configEnvs(annotations.configResource),
        akkaClusterEnvs(
          annotations.appName,
          discoveryMethod,
          annotations.modules,
          annotations.namespace,
          serviceResourceName,
          annotations.managementEndpointName.getOrElse(legacyAkkaManagementPortName),
          noOfReplicas,
          annotations.akkaClusterBootstrapSystemName,
          akkaClusterJoinExisting),
        externalServicesEnvs(annotations.modules, externalServices))

    private[kubernetes] def appNameEnvs(appName: Option[String]): Map[String, EnvironmentVariable] =
      appName.fold(Map.empty[String, EnvironmentVariable])(v => Map("RP_APP_NAME" -> LiteralEnvironmentVariable(v)))

    private[kubernetes] def appTypeEnvs(appType: Option[String], modules: Set[String]): Map[String, EnvironmentVariable] = {
      appType
        .toVector
        .map("RP_APP_TYPE" -> LiteralEnvironmentVariable(_)) ++ (
          if (modules.isEmpty) Seq.empty else Seq("RP_MODULES" -> LiteralEnvironmentVariable(modules.toVector.sorted.mkString(","))))
    }.toMap

    private[kubernetes] def akkaClusterEnvs(
      appName: Option[String],
      discoveryMethod: DiscoveryMethod,
      modules: Set[String],
      namespace: Option[String],
      serviceResourceName: String,
      managementEndpointName: String,
      noOfReplicas: Int,
      akkaClusterBootstrapSystemName: Option[String],
      akkaClusterJoinExisting: Boolean): Map[String, EnvironmentVariable] =
      if (!modules.contains(Module.AkkaClusterBootstrapping))
        Map.empty
      else
        Map(
          "RP_JAVA_OPTS" -> LiteralEnvironmentVariable(
            ((discoveryMethod match {
              case DiscoveryMethod.KubernetesApi =>
                List(
                  s"-Dakka.management.cluster.bootstrap.contact-point-discovery.discovery-method=kubernetes-api",
                  s"-Dakka.management.cluster.bootstrap.contact-point-discovery.port-name=$managementEndpointName",
                  // https://github.com/akka/akka-management/blob/v0.20.0/cluster-bootstrap/src/main/resources/reference.conf
                  akkaClusterBootstrapSystemName match {
                    case Some(systemName) => s"-Dakka.management.cluster.bootstrap.contact-point-discovery.effective-name=$systemName"
                    case _ => s"-Dakka.management.cluster.bootstrap.contact-point-discovery.effective-name=$serviceResourceName"
                  },
                  "-Dakka.discovery.kubernetes-api.pod-label-selector=akka.lightbend.com/service-name=%s")
              case DiscoveryMethod.AkkaDns =>
                List(
                  s"-Dakka.management.cluster.bootstrap.contact-point-discovery.discovery-method=akka-dns",
                  s"-Dakka.management.cluster.bootstrap.contact-point-discovery.port-name=$managementEndpointName",
                  appName match {
                    case Some(name) => s"-Dakka.management.cluster.bootstrap.contact-point-discovery.service-name=$name-internal"
                    case _ => sys.error("appName was expected")
                  })
            }) ++
              List(
                s"-Dakka.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr=$noOfReplicas",
                s"${if (akkaClusterJoinExisting) "-Dakka.management.cluster.bootstrap.form-new-cluster=false" else ""}"))
              .filter(_.nonEmpty)
              .mkString(" ")),
          "RP_DYN_JAVA_OPTS" -> LiteralEnvironmentVariable(
            Seq(
              "-Dakka.discovery.kubernetes-api.pod-namespace=$RP_NAMESPACE")
              .filter(_.nonEmpty)
              .mkString(" ")))

    private[kubernetes] def configEnvs(config: Option[String]): Map[String, EnvironmentVariable] =
      config
        .map(c => Map("RP_JAVA_OPTS" -> LiteralEnvironmentVariable(s"-Dconfig.resource=$c")))
        .getOrElse(Map.empty)

    private[kubernetes] def externalServicesEnvs(modules: Set[String], externalServices: Map[String, Seq[String]]): Map[String, EnvironmentVariable] =
      if (!modules.contains(Module.ServiceDiscovery))
        Map.empty
      else
        Map(
          "RP_JAVA_OPTS" -> LiteralEnvironmentVariable(
            externalServices
              .flatMap {
                case (name, addresses) =>
                  // We allow '/' as that's the convention used: $serviceName/$endpoint
                  // We allow '_' as it's currently used for Lagom defaults, i.e. "cas_native"

                  val arguments =
                    for {
                      (address, i) <- addresses.zipWithIndex
                    } yield s"-Dcom.lightbend.platform-tooling.service-discovery.external-service-addresses.${serviceName(name, Set('/', '_'))}.$i=$address"

                  arguments
              }
              .mkString(" ")))

    private[kubernetes] def versionEnvs(version: String): Map[String, EnvironmentVariable] =
      Map(
        "RP_APP_VERSION" -> LiteralEnvironmentVariable(version))

    private[kubernetes] def mergeEnvs(envs: Map[String, EnvironmentVariable]*): Map[String, EnvironmentVariable] = {
      envs.foldLeft(Map.empty[String, EnvironmentVariable]) {
        case (a1, n) =>
          n.foldLeft(a1) {
            case (a2, (key, LiteralEnvironmentVariable(v))) if ConcatLiteralEnvs.contains(key) =>
              a2.updated(key, a2.get(key) match {
                case Some(LiteralEnvironmentVariable(ov)) => LiteralEnvironmentVariable(s"$ov $v".trim)
                case _ => LiteralEnvironmentVariable(v)
              })

            case (a2, (key, value)) =>
              a2.updated(key, value)
          }
      }
    }
  }

  /**
   * CLI (depending on args) will generate config that causes akka-management to use one of these label names.
   *
   * If the akkaClusterJoinExisting flag is provided, these labels are removed from the pod template so that
   * it isn't used for bootstrap.
   */
  private[kubernetes] val PodDiscoveryLabels = Set("app", "actorSystemName")

  /**
   * Represents possible values for imagePullPolicy field within the Kubernetes pod template.
   */
  object ImagePullPolicy extends Enumeration {
    val Never, IfNotPresent, Always = Value
  }

  object RestartPolicy extends Enumeration {
    // What is default RestartPolicy depends on pod controller type, so define extra value "Default" here
    val Never, OnFailure, Always, Default = Value
  }

  case class ResourceLimits(cpu: Option[Double], memory: Option[Long])

  private[kubernetes] val VersionSeparator = "-v"

  implicit def imagePullPolicyEncode = EncodeJson[ImagePullPolicy.Value] {
    case ImagePullPolicy.Never => "Never".asJson
    case ImagePullPolicy.IfNotPresent => "IfNotPresent".asJson
    case ImagePullPolicy.Always => "Always".asJson
  }

  implicit def restartPolicyEncode = EncodeJson[RestartPolicy.Value] {
    case RestartPolicy.Never => jString("Never")
    case RestartPolicy.OnFailure => jString("OnFailure")
    case RestartPolicy.Always => jString("Always")
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

  implicit def secretKeyRefEnvironmentVariableEncode = EncodeJson[SecretKeyRefEnvironmentVariable] { env =>
    Json(
      "valueFrom" -> Json(
        "secretKeyRef" -> Json(
          "name" -> env.name.asJson,
          "key" -> env.key.asJson)))
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
    case v: SecretKeyRefEnvironmentVariable => v.asJson
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

  implicit def assignedEncode: EncodeJson[AssignedPort] = EncodeJson[AssignedPort] { assigned =>
    assigned.endpoint match {
      case UdpEndpoint(_, _, _) =>
        Json(
          "containerPort" -> assigned.port.asJson,
          "name" -> serviceName(assigned.endpoint.name).asJson,
          "protocol" -> "UDP".asJson)
      case _ =>
        Json(
          "containerPort" -> assigned.port.asJson,
          "name" -> serviceName(assigned.endpoint.name).asJson)
    }
  }

  implicit def endpointsEncode = EncodeJson[Map[String, Endpoint]] { endpoints =>
    AssignedPort.assignPorts(endpoints)
      .toList
      .sortBy(_.endpoint.index)
      .map(_.asJson)
      .asJson
  }

  implicit def resourceLimitsEncode = EncodeJson[ResourceLimits] {
    case ResourceLimits(cpu, memory) =>
      val memoryJson = memory.map({ v => Json("memory" -> v.asJson) }).getOrElse(jEmptyObject)
      val cpuJson = cpu.map({ v => Json("cpu" -> v.asJson) }).getOrElse(jEmptyObject)
      if (cpu.isEmpty && memory.isEmpty) {
        jEmptyObject
      } else {
        Json(
          "resources" -> Json(
            "limits" -> cpuJson.deepmerge(memoryJson),
            "requests" -> cpuJson.deepmerge(memoryJson)))
      }
  }

  /**
   * Builds [[PodTemplate]] resource.
   */
  def generate(
    annotations: Annotations,
    apiVersion: String,
    application: Option[String],
    imageName: String,
    imagePullPolicy: ImagePullPolicy.Value,
    noOfReplicas: Int,
    restartPolicy: RestartPolicy.Value,
    externalServices: Map[String, Seq[String]],
    deploymentType: DeploymentType,
    discoveryMethod: DiscoveryMethod,
    akkaClusterJoinExisting: Boolean,
    applicationArgs: Option[Seq[String]],
    appName: String,
    appNameVersion: String,
    labels: Map[String, String]): PodTemplate = {
    val serviceResourceName =
      deploymentType match {
        case CanaryDeploymentType => appName
        case BlueGreenDeploymentType => appNameVersion
        case RollingDeploymentType => appName
      }

    val secretNames =
      annotations
        .secrets
        .map(_.name)
        .distinct
        .map(ns => (ns, serviceName(ns), s"secret-${serviceName(ns)}"))
        .toList

    val resourceLimits = ResourceLimits(annotations.cpu, annotations.memory)

    val enableChecks =
      annotations.modules.contains(Module.Status) && annotations.modules.contains(Module.AkkaManagement)

    lazy val managementPortName =
      annotations
        .managementEndpointName
        .getOrElse(legacyAkkaManagementPortName)

    val livenessProbe =
      if (enableChecks)
        Json("livenessProbe" ->
          Json(
            "httpGet" -> Json(
              "path" -> jString(HealthCheckUrl),
              "port" -> jString(managementPortName)),
            "periodSeconds" -> jNumber(StatusPeriodSeconds),
            "initialDelaySeconds" -> jNumber(LivenessInitialDelaySeconds)))
      else
        jEmptyObject

    val readinessProbe =
      if (enableChecks)
        Json("readinessProbe" ->
          Json(
            "httpGet" -> Json(
              "path" -> jString(ReadyCheckUrl),
              "port" -> jString(managementPortName)),
            "periodSeconds" -> jNumber(StatusPeriodSeconds)))
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
            Json(
              "command" -> jArrayElements(jString(c.head)),
              "args" -> jArray(c.tail.map(jString).toList))
      }

    val labelsToUse =
      if (akkaClusterJoinExisting)
        PodDiscoveryLabels.foldLeft(labels)(_ - _)
      else
        labels

    val annotationsToUse = Option(
      annotations
        .annotations
        .map(a => (a.key, jString(a.value)))
        .toMap)
      .filter(_.nonEmpty)

    PodTemplate(
      Json(
        "metadata" -> (
          ("labels" -> labelsToUse.asJson) ->:
          ("annotations" :=? annotationsToUse) ->?: jEmptyObject),
        "spec" -> Json(
          "restartPolicy" -> restartPolicy.asJson,
          "containers" -> jArrayElements(
            Json(
              "name" -> appName.asJson,
              "image" -> imageName.asJson,
              "imagePullPolicy" -> imagePullPolicy.asJson,
              "env" -> RpEnvironmentVariables.mergeEnvs(
                annotations.environmentVariables ++
                  RpEnvironmentVariables.envs(annotations, serviceResourceName, noOfReplicas, externalServices, akkaClusterJoinExisting, discoveryMethod)).asJson,
              "ports" -> annotations.endpoints.asJson,
              "volumeMounts" -> secretNames
                .map {
                  case (_, secretServiceName, volumeName) =>
                    Json(
                      "mountPath" -> s"/rp/secrets/$secretServiceName".asJson,
                      "name" -> jString(volumeName))
                }
                .asJson)
              .deepmerge(commandArgs)
              .deepmerge(readinessProbe)
              .deepmerge(livenessProbe)
              .deepmerge(resourceLimits.asJson)),
          "volumes" -> secretNames
            .map {
              case (secretName, _, volumeName) =>
                Json(
                  "name" -> jString(volumeName),
                  "secret" -> Json("secretName" -> jString(secretName)))
            }.asJson)))
  }
}

case class PodTemplate(json: Json)
