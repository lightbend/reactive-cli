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

package com.lightbend.rp.reactivecli.runtime.marathon

import com.lightbend.rp.reactivecli.annotations._
import scala.collection.immutable.Seq

object RpEnvironmentVariables {
  /**
   * Environment variables in this set will be space-concatenated when the various environment variable
   * maps are merged.
   */
  private val ConcatLiteralEnvs = Set("RP_JAVA_OPTS")

  /**
   * Generates pod environment variables specific for RP applications.
   */
  def envs(namespace: Option[String], annotations: Annotations, serviceResourceName: String, noOfReplicas: Int, externalServices: Map[String, Seq[String]], akkaClusterJoinExisting: Boolean): Map[String, String] =
    mergeEnvs(
      Map("RP_PLATFORM" -> "mesos") ++
        namespace.map(ns => "RP_NAMESPACE" -> ns),
      appNameEnvs(annotations.appName),
      annotations.version.fold(Map.empty[String, String])(versionEnvs),
      appTypeEnvs(annotations.appType, annotations.modules),
      configEnvs(annotations.configResource),
      endpointEnvs(annotations.endpoints),
      akkaClusterEnvs(annotations.modules, annotations.namespace, serviceResourceName, noOfReplicas, annotations.akkaClusterBootstrapSystemName, akkaClusterJoinExisting),
      externalServicesEnvs(annotations.modules, externalServices))

  private def appNameEnvs(appName: Option[String]): Map[String, String] =
    appName.fold(Map.empty[String, String])(v => Map("RP_APP_NAME" -> v))

  private def appTypeEnvs(appType: Option[String], modules: Set[String]): Map[String, String] = {
    appType
      .toVector
      .map("RP_APP_TYPE" -> _) ++ (
        if (modules.isEmpty) Seq.empty else Seq("RP_MODULES" -> modules.toVector.sorted.mkString(",")))
  }.toMap

  private def akkaClusterEnvs(modules: Set[String], namespace: Option[String], serviceResourceName: String, noOfReplicas: Int, akkaClusterBootstrapSystemName: Option[String], akkaClusterJoinExisting: Boolean): Map[String, String] =
    if (!modules.contains(Module.AkkaClusterBootstrapping))
      Map.empty
    else
      Map(
        "RP_JAVA_OPTS" -> Seq(
          s"-Dakka.management.cluster.bootstrap.contact-point-discovery.discovery-method=marathon-api",
          s"-Dakka.management.cluster.bootstrap.contact-point-discovery.effective-name=$serviceResourceName",
          s"-Dakka.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr=$noOfReplicas",
          akkaClusterBootstrapSystemName.fold("-Dakka.discovery.marathon-api.app-label-query=APP_NAME==%s")(systemName => s"-Dakka.discovery.marathon-api.app-label-query=ACTOR_SYSTEM_NAME==$systemName"),
          s"${if (akkaClusterJoinExisting) "-Dakka.management.cluster.bootstrap.form-new-cluster=false" else ""}")
          .filter(_.nonEmpty)
          .mkString(" "))

  private def configEnvs(config: Option[String]): Map[String, String] =
    config
      .map(c => Map("RP_JAVA_OPTS" -> s"-Dconfig.resource=$c"))
      .getOrElse(Map.empty)

  private def externalServicesEnvs(modules: Set[String], externalServices: Map[String, Seq[String]]): Map[String, String] =
    if (!modules.contains(Module.ServiceDiscovery))
      Map.empty
    else
      Map(
        "RP_JAVA_OPTS" ->
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
          .mkString(" "))

  private def versionEnvs(version: String): Map[String, String] =
    Map(
      "RP_APP_VERSION" -> version)

  private def endpointEnvs(endpoints: Map[String, Endpoint]): Map[String, String] =
    if (endpoints.isEmpty)
      Map(
        "RP_ENDPOINTS_COUNT" -> "0")
    else
      Map(
        "RP_ENDPOINTS_COUNT" -> endpoints.size.toString,
        "RP_ENDPOINTS" ->
          endpoints.values.toList
          .sortBy(_.index)
          .map(v => envVarName(v.name))
          .mkString(",")) ++
        endpointPortEnvs(endpoints)

  private def endpointPortEnvs(endpoints: Map[String, Endpoint]): Map[String, String] =
    endpoints
      .flatMap {
        case (_, endpoint) =>
          Seq(
            s"RP_ENDPOINT_${envVarName(endpoint.name)}_HOST" -> "$HOST",
            s"RP_ENDPOINT_${envVarName(endpoint.name)}_BIND_HOST" -> "0.0.0.0",
            s"RP_ENDPOINT_${envVarName(endpoint.name)}_PORT" -> s"$$PORT_${portEnvName(endpoint.name)}",
            s"RP_ENDPOINT_${envVarName(endpoint.name)}_BIND_PORT" -> s"$$PORT_${portEnvName(endpoint.name)}",
            s"RP_ENDPOINT_${endpoint.index}_HOST" -> "$HOST",
            s"RP_ENDPOINT_${endpoint.index}_BIND_HOST" -> "0.0.0.0",
            s"RP_ENDPOINT_${endpoint.index}_PORT" -> s"$$PORT_${portEnvName(endpoint.name)}",
            s"RP_ENDPOINT_${endpoint.index}_BIND_PORT" -> s"$$PORT_${portEnvName(endpoint.name)}")
      }
      .toMap

  private def mergeEnvs(envs: Map[String, String]*): Map[String, String] = {
    envs.foldLeft(Map.empty[String, String]) {
      case (a1, n) =>
        n.foldLeft(a1) {
          case (a2, (key, v)) if ConcatLiteralEnvs.contains(key) =>
            a2.updated(key, a2.get(key) match {
              case Some(ov) => s"$ov $v".trim
              case _ => v
            })

          case (a2, (key, value)) =>
            a2.updated(key, value)
        }
    }
  }
}
