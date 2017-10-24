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

object IngressIstio {
  implicit def encodeHttpAcl(endpointName: String, port: Int) = EncodeJson[HttpAcl] { acl =>
    Json(
      "path" -> s"${acl.expression}.*".asJson,
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
  def generate(annotations: Annotations): Try[Json] =
    serviceName(annotations) match {
      case Some(appName) =>
        Success(
          Json(
            "apiVersion" -> "extensions/v1beta1".asJson,
            "kind" -> "Ingress".asJson,
            "metadata" -> Json(
              "name" -> appName.asJson,
              "annotations" -> Json(
                "kubernetes.io/ingress.class" -> "istio".asJson)),
            "spec" -> Json(
              "rules" -> annotations.endpoints.asJson)))
      case _ =>
        Failure(new IllegalArgumentException("Unable to generate Kubernetes ingress resource for Istio: application name is required"))
    }

}
