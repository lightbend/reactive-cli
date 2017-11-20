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
import com.lightbend.rp.reactivecli.annotations._
import scala.collection.immutable.Seq
import scala.util.{ Failure, Success, Try }

object Ingress {
  def encodeEndpoints(endpoints: Map[String, Endpoint], pathAppend: Option[String]): List[Json] = {
    val httpEndpoints =
      endpoints
        .collect {
          case (_, httpEndpoint: HttpEndpoint) => httpEndpoint
        }
        .toList

    for {
      endpoint <- httpEndpoints
      backend = Json("serviceName" -> endpointServiceName(endpoint).asJson, "servicePort" -> endpoint.port.asJson)
      ingress <- endpoint.ingress
      host <- if (ingress.hosts.isEmpty) Seq("") else ingress.hosts
      paths = if (ingress.paths.isEmpty) Seq("") else ingress.paths
    } yield {
      val base =
        if (host.isEmpty)
          Map.empty[String, Json]
        else
          Map("host" -> jString(host))

      val pathEntries =
        for {
          path <- paths
        } yield {
          val backendBase =
            if (path.isEmpty)
              Map.empty[String, Json]
            else
              Map("path" -> jString(path + pathAppend.getOrElse("")))

          jObjectAssocList(backendBase.updated("backend", backend).toList)
        }

      jObjectAssocList(base.updated("http", jSingleObject("paths", pathEntries.toList.asJson)).toList)
    }
  }

  /**
   * Generates the [[Ingress]] resources.
   */
  def generate(annotations: Annotations, ingressAnnotations: Map[String, String], pathAppend: Option[String]): Try[Ingress] =
    serviceName(annotations) match {
      case Some(appName) =>
        Success(
          Ingress(appName, Json(
            "apiVersion" -> "extensions/v1beta1".asJson,
            "kind" -> "Ingress".asJson,
            "metadata" -> Json(
              "name" -> appName.asJson).deepmerge(generateIngressAnnotations(ingressAnnotations)),
            "spec" -> Json(
              "rules" -> encodeEndpoints(annotations.endpoints, pathAppend).asJson))))
      case _ =>
        Failure(new IllegalArgumentException("Unable to generate Kubernetes ingress resource: application name is required"))
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
 * Represents the generated ingress resource.
 */
case class Ingress(name: String, payload: Json) extends GeneratedKubernetesResource {
  val resourceType = "ingress"
}