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
import scala.collection.immutable.Seq
import com.lightbend.rp.reactivecli.annotations.Annotations
import com.lightbend.rp.reactivecli.concurrent._
import utest._

import Argonaut._

object NamespaceJsonTest extends TestSuite {

  val tests = this{
    "json serialization" - {
      val annotations = Annotations(
        namespace = None,
        appName = None,
        appType = None,
        configResource = None,
        diskSpace = None,
        memory = None,
        cpu = None,
        endpoints = Map.empty,
        secrets = Seq.empty,
        privileged = false,
        environmentVariables = Map.empty,
        version = None,
        modules = Set.empty,
        akkaClusterBootstrapSystemName = None)

      "namespace present" - {
        val result = Namespace.generate(annotations.copy(namespace = Some("chirper")), "v1", None)

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
        assert(result.toOption.get.get == Namespace("chirper", expectedJson, None))
      }

      "namespace not present" - {
        val result = Namespace.generate(annotations.copy(namespace = None), "v1", None)

        assert(result.isSuccess)
        assert(result.toOption.get.isEmpty)
      }

      "jq works" - {
        val result = Namespace.generate(annotations.copy(namespace = Some("chirper")), "v1", Some(".jq=\"testing\""))

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
            |  },
            |  "jq": "testing"
            |}
          """.stripMargin.parse.right.get

        result.toOption.get.get.payload.map { generatedJson =>
          assert(expectedJson == generatedJson)
        }
      }
    }
  }
}
