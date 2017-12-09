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
import com.lightbend.rp.reactivecli.annotations.Annotations
import scalaz._

import Argonaut._
import Scalaz._

object Namespace {
  /**
   * Builds [[Namespace]] resource.
   */
  def generate(
    annotations: Annotations,
    apiVersion: String,
    jqExpression: Option[String]): ValidationNel[String, Option[Namespace]] =
    annotations
      .namespace
      .map { rawNs =>
        val ns = serviceName(rawNs)

        Namespace(
          ns,
          Json(
            "apiVersion" -> apiVersion.asJson,
            "kind" -> "Namespace".asJson,
            "metadata" -> Json(
              "name" -> ns.asJson,
              "labels" -> Json(
                "name" -> ns.asJson))),
          jqExpression)
      }
      .successNel
}

case class Namespace(name: String, json: Json, jqExpression: Option[String]) extends GeneratedKubernetesResource {
  val resourceType = "namespace"
}
