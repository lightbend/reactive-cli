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

import argonaut.Argonaut._
import argonaut._
import com.lightbend.rp.reactivecli.annotations.HttpEndpoint.HttpAcl
import com.lightbend.rp.reactivecli.annotations._

import scala.util.{ Failure, Success, Try }

object Ingress {
  def encodeHttpAcl(endpointName: String, port: Int, pathAppend: Option[String]) = EncodeJson[HttpAcl] { acl =>
    Json(
      "path" -> s"${acl.expression}${pathAppend.getOrElse("")}".asJson,
      "backend" -> Json(
        "serviceName" -> endpointName.asJson,
        "servicePort" -> port.asJson))
  }

  def encodeHttpEndpointIngressRule(pathAppend: Option[String]) = EncodeJson[HttpEndpoint] { endpoint =>
    val name = endpointName(endpoint).toLowerCase
    implicit val httpAclJsonEncoder: EncodeJson[HttpAcl] = encodeHttpAcl(name, endpoint.port, pathAppend)

    Json(
      "http" -> Json(
        "paths" -> endpoint.acls
          .map(_.asJson)
          .toList
          .asJson))
  }

  def encodeEndpointsIngressRule(pathAppend: Option[String]) = EncodeJson[Map[String, Endpoint]] { endpoints =>
    implicit val httpEndpointIngressRuleJsonEncoder: EncodeJson[HttpEndpoint] = encodeHttpEndpointIngressRule(pathAppend)

    endpoints
      .collect {
        case (_, httpEndpoint: HttpEndpoint) => httpEndpoint.asJson
      }
      .toList
      .asJson
  }

  /**
   * Generates the [[Ingress]] resource.
   */
  def generate(annotations: Annotations, ingressAnnotations: Map[String, String], pathAppend: Option[String]): Try[Ingress] =
    serviceName(annotations) match {
      case Some(appName) =>
        implicit val endpointIngressRuleJsonEncoder: EncodeJson[Map[String, Endpoint]] = encodeEndpointsIngressRule(pathAppend)

        Success(
          Ingress(appName, Json(
            "apiVersion" -> "extensions/v1beta1".asJson,
            "kind" -> "Ingress".asJson,
            "metadata" -> Json(
              "name" -> appName.asJson).deepmerge(generateIngressAnnotations(ingressAnnotations)),
            "spec" -> Json(
              "rules" -> annotations.endpoints.asJson))))
      case _ =>
        Failure(new IllegalArgumentException("Unable to generate Kubernetes ingress resource for Istio: application name is required"))
    }

  private def generateIngressAnnotations(ingressAnnotations: Map[String, String]): Json =
    if (ingressAnnotations.isEmpty)
      Json.jEmptyObject
    else
      Json(
        "annotations" -> ingressAnnotations
          .map {
            case (k, v) =>
              Json(k -> v.asJson)
          }
          .reduce(_.deepmerge(_)))

}

/**
 * Represents the generated Istion ingress resource.
 */
case class Ingress(name: String, payload: Json) extends GeneratedKubernetesResource {
  val resourceType = "ingress"
}