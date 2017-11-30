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
import scala.collection.immutable.Seq
import com.lightbend.rp.reactivecli.annotations.Annotations
import utest._

object NamespaceJsonTest extends TestSuite {

  val tests = this{
    "json serialization" - {
      val annotations = new Annotations(
        namespace = None,
        appName = None,
        appType = None,
        diskSpace = None,
        memory = None,
        nrOfCpus = None,
        endpoints = Map.empty,
        secrets = Seq.empty,
        volumes = Map.empty,
        privileged = false,
        healthCheck = None,
        readinessCheck = None,
        environmentVariables = Map.empty,
        version = None,
        modules = Set.empty)

      "namespace present" - {
        val result = Namespace.generate(annotations.copy(namespace = Some("chirper")), "v1")

        assert(result.isSuccess)

        val expectedJson =
          """
            |{
            |  "apiVersion": "v1",
            |  "kind": "Namespace",
            |  "metadata": {
            |    "name": "chirper",
            |    "labels": {
            |      "name": "chirper"
            |    }
            |  }
            |}
          """.stripMargin.parse.right.get
        assert(result.get.get == Namespace("chirper", expectedJson))
      }

      "namespace not present" - {
        val result = Namespace.generate(annotations.copy(namespace = None), "v1")

        assert(result.isSuccess)
        assert(result.get.isEmpty)
      }
    }
  }
}
