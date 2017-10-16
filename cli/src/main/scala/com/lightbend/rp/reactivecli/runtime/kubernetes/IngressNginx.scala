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
import com.lightbend.rp.reactivecli.annotations.HttpEndpoint.HttpAcl
import com.lightbend.rp.reactivecli.annotations._

import scala.util.{ Failure, Success, Try }

object IngressNginx {
  implicit def encodeHttpAcl(endpointName: String, port: Int) = EncodeJson[HttpAcl] { acl =>
    Json(
      "path" -> acl.expression.asJson,
      "backend" -> Json(
        "serviceName" -> endpointName.asJson,
        "servicePort" -> port.asJson))
  }

  implicit def encodeHttpEndpointIngressRule = EncodeJson[HttpEndpoint] { endpoint =>
    val name = endpointName(endpoint)
    Json(
      "http" -> Json(
        "paths" -> endpoint.acls
          .map(_.asJson(encodeHttpAcl(name, endpoint.port)))
          .toList
          .asJson))
  }

  implicit def encodeEndpointsIngressRule = EncodeJson[Map[String, Endpoint]] { endpoints =>
    endpoints
      .collect {
        case (_, httpEndpoint: HttpEndpoint) => httpEndpoint.asJson
      }
      .toList
      .asJson
  }
  def generate(annotations: Annotations, tlsSecretName: Option[String], sslRedirect: Boolean): Try[Json] =
    serviceName(annotations) match {
      case Some(appName) =>
        Success(
          Json(
            "apiVersion" -> "extensions/v1beta1".asJson,
            "kind" -> "Ingress".asJson,
            "metadata" -> Json(
              "name" -> appName.asJson,
              "annotations" -> Json(
                "ingress.kubernetes.io/ssl-redirect" -> sslRedirect.toString.asJson)),
            "spec" -> Json(
              "rules" -> annotations.endpoints.asJson).deepmerge(generateTlsSecret(tlsSecretName))))
      case _ =>
        Failure(new IllegalArgumentException("Unable to generate Kubernetes ingress resource for Nginx: application name is required"))
    }

  private def generateTlsSecret(tlsSecretName: Option[String]): Json =
    tlsSecretName.fold(jEmptyObject)(v => Json("tls" -> List(Json("secretName" -> v.asJson)).asJson))

}
