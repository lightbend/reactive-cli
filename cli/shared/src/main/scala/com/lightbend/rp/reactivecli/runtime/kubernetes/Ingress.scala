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
import com.lightbend.rp.reactivecli.runtime._
import scala.collection.immutable.Seq
import scalaz._

import Argonaut._
import Scalaz._

object Ingress {
  case class EncodedEndpoint(serviceName: String, servicePort: Int, paths: Seq[String], host: Option[String])

  def encodeEndpoints(
    appName: String,
    endpoints: Map[String, Endpoint],
    pathAppend: Option[String],
    hostsOverride: Option[Seq[String]]): List[EncodedEndpoint] = {

    val ports = AssignedPort.assignPorts(endpoints)

    val httpEndpoints =
      endpoints
        .collect {
          case (_, httpEndpoint: HttpEndpoint) => httpEndpoint
        }
        .toList

    val append = pathAppend.getOrElse("")

    for {
      endpoint <- httpEndpoints
      port <- ports.find(_.endpoint == endpoint).toVector
      ingress <- endpoint.ingress
      host <- hostsOverride.getOrElse(if (ingress.hosts.isEmpty) Seq("") else ingress.hosts)
      paths = if (ingress.paths.isEmpty) Seq("") else ingress.paths
    } yield {
      val pathEntries =
        for {
          path <- paths
        } yield if (path.isEmpty)
          ""
        else
          path + (if (path.endsWith("/") && append.startsWith("/")) append.drop(1) else append)

      EncodedEndpoint(appName, port.port, pathEntries, if (host.isEmpty) None else Some(host))
    }
  }

  def renderEndpoints(endpoints: Seq[EncodedEndpoint]): Json = {
    case class Path(serviceName: String, servicePort: Int, path: String) {
      def depthAndLength: (Int, Int) = pathDepthAndLength(path)
    }

    val byHost =
      endpoints
        .groupBy(_.host)
        .toVector
        .sortBy(_._1)
        .map {
          case (host, endpoints) =>
            val paths =
              endpoints
                .foldLeft(Seq.empty[Path]) {
                  case (ac, e) =>
                    ac ++ e.paths.map(p => Path(e.serviceName, e.servicePort, p))
                }
                .distinct
                .sortBy(_.depthAndLength)
                .reverse

            host -> paths
        }

    jArray(
      byHost.toList.map {
        case (host, paths) =>
          host
            .fold(jEmptyObject)(h => jObjectFields("host" -> jString(h)))
            .deepmerge(
              jObjectFields("http" ->
                jObjectFields(
                  "paths" -> jArray(
                    paths.toList.map(p =>
                      (if (p.path.nonEmpty) jObjectFields("path" -> jString(p.path)) else jEmptyObject)
                        .deepmerge(
                          jObjectFields(
                            "backend" -> jObjectFields(
                              "serviceName" -> jString(p.serviceName),
                              "servicePort" -> jNumber(p.servicePort)))))))))
      })
  }

  /**
   * Generates the [[Ingress]] resources.
   */
  def generate(
    annotations: Annotations,
    apiVersion: String,
    hosts: Option[Seq[String]],
    ingressAnnotations: Map[String, String],
    jqExpression: Option[String],
    name: Option[String],
    pathAppend: Option[String],
    tlsSecrets: Seq[String]): ValidationNel[String, Option[Ingress]] = {
    annotations
      .appNameValidation
      .map { rawAppName =>
        val appName = serviceName(rawAppName)
        val actualName = name.map(serviceName(_)).getOrElse(appName)
        val encodedEndpoints = encodeEndpoints(appName, annotations.endpoints, pathAppend, hosts)

        if (encodedEndpoints.isEmpty)
          None
        else
          Some(
            Ingress(
              actualName,
              encodedEndpoints,
              Json(
                "apiVersion" -> apiVersion.asJson,
                "kind" -> "Ingress".asJson,
                "metadata" -> Json(
                  "name" -> actualName.asJson)
                  .deepmerge(generateIngressAnnotations(ingressAnnotations))
                  .deepmerge(generateNamespaceAnnotation(annotations.namespace)),
                "spec" -> Json(
                  "rules" -> renderEndpoints(encodedEndpoints)).deepmerge(
                    if (tlsSecrets.isEmpty) jEmptyObject else jObjectFields("tls" -> jArray(
                      tlsSecrets.toList.map(s => jObjectFields("secretName" -> jString(s))))))),
              jqExpression))
      }
  }

  def merge(name: String, a: Ingress, b: Ingress): Ingress = {
    val endpoints = a.endpoints ++ b.endpoints
    val appName = serviceName(name)

    val merged = a.json.deepmerge(b.json)
    val maybeUpdated = -(merged.hcursor --\ "metadata" --\ "name" := jString(appName))
    val updated =
      maybeUpdated
        .getOrElse(merged)
        .deepmerge(jObjectFields("spec" -> jObjectFields("rules" -> renderEndpoints(endpoints))))

    Ingress(appName, endpoints, updated, b.jqExpression)
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

  private def generateNamespaceAnnotation(namespace: Option[String]): Json =
    namespace.fold(jEmptyObject)(ns => Json("namespace" -> serviceName(ns).asJson))
}

/**
 * Represents the generated ingress resource.
 */
case class Ingress(name: String, endpoints: List[Ingress.EncodedEndpoint], json: Json, jqExpression: Option[String]) extends GeneratedKubernetesResource {
  val resourceType = "ingress"
}
