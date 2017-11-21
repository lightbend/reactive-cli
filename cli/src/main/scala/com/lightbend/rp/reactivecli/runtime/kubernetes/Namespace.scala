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
import com.lightbend.rp.reactivecli.annotations.Annotations

import scala.util.{ Success, Try }

object Namespace {
  /**
   * Builds [[Namespace]] resource.
   */
  def generate(annotations: Annotations): Try[Option[Namespace]] =
    Success(
      annotations.namespace.map { ns =>
        Namespace(ns, Json(
          "apiVersion" -> "v1".asJson,
          "kind" -> "Namespace".asJson,
          "metadata" -> Json(
            "name" -> ns.asJson,
            "labels" -> Json(
              "name" -> ns.asJson))))
      })

}

case class Namespace(name: String, payload: Json) extends GeneratedKubernetesResource {
  val resourceType = "namespace"
}
