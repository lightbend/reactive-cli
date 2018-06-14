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
import com.lightbend.rp.reactivecli.annotations._
import com.lightbend.rp.reactivecli.argparse._
import com.lightbend.rp.reactivecli.json.JsonTransformExpression
import scalaz._

import Argonaut._
import Scalaz._

object Service {
  def encodeEndpoint(endpoint: Endpoint, port: AssignedPort): Json = {
    val protocol = endpoint match {
      case v: HttpEndpoint => "TCP"
      case v: TcpEndpoint => "TCP"
      case v: UdpEndpoint => "UDP"
    }

    Json(
      "name" -> serviceName(endpoint.name).asJson,
      "port" -> port.port.asJson,
      "protocol" -> protocol.asJson,
      "targetPort" -> port.port.asJson)
  }

  implicit def encodeEndpoints = EncodeJson[Map[String, Endpoint]] { endpoints =>
    val ports = AssignedPort.assignPorts(endpoints)
    val encoded =
      for {
        (_, endpoint) <- endpoints
        port <- ports.find(_.endpoint == endpoint)
      } yield encodeEndpoint(endpoint, port)

    encoded.toList.asJson
  }

  /**
   * Generates the [[Service]] resource.
   */
  def generate(
    annotations: Annotations,
    apiVersion: String,
    clusterIp: Option[String],
    deploymentType: DeploymentType,
    jqExpression: Option[JsonTransformExpression],
    loadBalancerIp: Option[String],
    serviceType: Option[String]): ValidationNel[String, Option[Service]] =
    (annotations.appNameValidation |@| annotations.versionValidation) { (rawAppName, version) =>
      // FIXME there's a bit of code duplicate in Deployment
      val appName = serviceName(rawAppName)
      val appNameVersion = serviceName(s"$appName${PodTemplate.VersionSeparator}$version")

      val selector =
        deploymentType match {
          case CanaryDeploymentType => Json("appName" -> appName.asJson)
          case RollingDeploymentType => Json("appName" -> appName.asJson)
          case BlueGreenDeploymentType => Json("appNameVersion" -> appNameVersion.asJson)
        }

      if (annotations.endpoints.isEmpty)
        None
      else
        Some(
          Service(
            appName,
            Json(
              "apiVersion" -> apiVersion.asJson,
              "kind" -> "Service".asJson,
              "metadata" -> Json(
                "labels" -> Json(
                  "appName" -> appName.asJson),
                "name" -> appName.asJson)
                .deepmerge(
                  annotations.namespace.fold(jEmptyObject)(ns => Json("namespace" -> serviceName(ns).asJson))),
              "spec" -> Json(
                "ports" -> annotations.endpoints.asJson,
                "selector" -> selector)
                .deepmerge(clusterIp.fold(jEmptyObject)(cIp => Json("clusterIP" -> jString(cIp))))
                .deepmerge(serviceType.fold(jEmptyObject)(svcType => Json("type" -> jString(svcType))))
                .deepmerge(loadBalancerIp.fold(jEmptyObject)(lbIp => Json("loadBalancerIP" -> jString(lbIp))))),
            jqExpression))
    }
}

/**
 * Represents the generated Kubernetes service resource.
 */
case class Service(name: String, json: Json, jqExpression: Option[JsonTransformExpression]) extends GeneratedKubernetesResource {
  val resourceType = "service"
}
