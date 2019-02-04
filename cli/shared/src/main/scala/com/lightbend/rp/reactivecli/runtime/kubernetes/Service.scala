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
import com.lightbend.rp.reactivecli.json.JsonTransform
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
    discoveryMethod: DiscoveryMethod,
    jsonTransform: JsonTransform,
    loadBalancerIp: Option[String],
    serviceType: Option[String]): ValidationNel[String, List[Service]] =
    (annotations.appNameValidation |@| annotations.versionValidation) { (rawAppName, version) =>
      // FIXME there's a bit of code duplicate in Deployment
      val appName = serviceName(rawAppName)
      val internalAppname = appName + "-internal"
      val appNameVersion = serviceName(s"$appName${PodTemplate.VersionSeparator}$version")

      val selector =
        deploymentType match {
          case CanaryDeploymentType => Json("app" -> appName.asJson)
          case RollingDeploymentType => Json("app" -> appName.asJson)
          case BlueGreenDeploymentType => Json("appNameVersion" -> appNameVersion.asJson)
        }

      def svc(endpoints: Map[String, Endpoint]) = Service(
        appName,
        Json(
          "apiVersion" -> apiVersion.asJson,
          "kind" -> "Service".asJson,
          "metadata" -> Json(
            "labels" -> Json(
              "app" -> appName.asJson),
            "name" -> appName.asJson)
            .deepmerge(
              annotations.namespace.fold(jEmptyObject)(ns => Json("namespace" -> serviceName(ns).asJson))),
          "spec" -> Json(
            "ports" -> endpoints.asJson,
            "selector" -> selector)
            .deepmerge(clusterIp.fold(jEmptyObject)(cIp => Json("clusterIP" -> jString(cIp))))
            .deepmerge(serviceType.fold(jEmptyObject)(svcType => Json("type" -> jString(svcType))))
            .deepmerge(loadBalancerIp.fold(jEmptyObject)(lbIp => Json("loadBalancerIP" -> jString(lbIp))))),
        jsonTransform)

      def headlessService(endpoints: Map[String, Endpoint]) = Service(
        internalAppname,
        Json(
          "apiVersion" -> apiVersion.asJson,
          "kind" -> "Service".asJson,
          "metadata" -> Json(
            "labels" -> Json(
              "app" -> appName.asJson),
            "annotations" -> Json(
              "service.alpha.kubernetes.io/tolerate-unready-endpoints" -> jString("true")),
            "name" -> internalAppname.asJson)
            .deepmerge(
              annotations.namespace.fold(jEmptyObject)(ns => Json("namespace" -> serviceName(ns).asJson))),
          "spec" -> Json(
            "ports" -> endpoints.asJson,
            "selector" -> selector,
            "clusterIP" -> jString("None"),
            "publishNotReadyAddresses" -> jTrue)),
        jsonTransform)

      if (annotations.endpoints.isEmpty) List()
      else if (discoveryMethod == DiscoveryMethod.AkkaDns) List(
        headlessService(annotations.headlessEndpoints),
        svc(annotations.publicEndpoints))
      else List(svc(annotations.endpoints))
    }
}

/**
 * Represents the generated Kubernetes service resource.
 */
case class Service(name: String, json: Json, jsonTransform: JsonTransform) extends GeneratedKubernetesResource {
  val resourceType = "service"
}
