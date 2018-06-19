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
import com.lightbend.rp.reactivecli.argparse.{ CanaryDeploymentType, BlueGreenDeploymentType, RollingDeploymentType }
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.json.{ JsonTransform, JsonTransformExpression }
import scala.collection.immutable.Seq
import utest._

import Argonaut._

object ServiceJsonTest extends TestSuite {

  val annotations = Annotations(
    namespace = Some("chirper"),
    applications = Vector.empty,
    appName = Some("friendimpl"),
    appType = None,
    configResource = None,
    diskSpace = Some(65536L),
    memory = Some(8192L),
    cpu = Some(0.5D),
    endpoints = Map(
      "ep1" -> TcpEndpoint(0, "ep1", 1234)),
    secrets = Seq.empty,
    privileged = true,
    environmentVariables = Map(
      "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
    version = Some("3.2.1-SNAPSHOT"),
    modules = Set.empty,
    akkaClusterBootstrapSystemName = None)

  val tests = this{
    "json serialization" - {
      "empty" - {
        val result = Service.generate(annotations.copy(endpoints = Map.empty), "v1", clusterIp = None, CanaryDeploymentType, JsonTransform.noop, None, None).toOption.get.isEmpty

        assert(result)
      }

      "deploymentType" - {
        "Canary" - {
          Service
            .generate(annotations, "v1", clusterIp = None, CanaryDeploymentType, JsonTransform.noop, None, None)
            .toOption
            .get
            .get
            .payload
            .map { j =>
              val result = (j.hcursor --\ "spec" --\ "selector" --\ "appName").focus
              val expected = Some(jString("friendimpl"))

              assert(result == expected)
            }
        }

        "BlueGreen" - {
          Service
            .generate(annotations, "v1", clusterIp = None, BlueGreenDeploymentType, JsonTransform.noop, None, None)
            .toOption
            .get
            .get
            .payload
            .map(j => assert((j.hcursor --\ "spec" --\ "selector" --\ "appNameVersion").focus.contains(jString("friendimpl-v3-2-1-snapshot"))))
        }

        "Rolling" - {
          Service
            .generate(annotations, "v1", clusterIp = None, RollingDeploymentType, JsonTransform.noop, None, None)
            .toOption
            .get
            .get
            .payload
            .map(j => assert((j.hcursor --\ "spec" --\ "selector" --\ "appName").focus.contains(jString("friendimpl"))))
        }
      }

      "jq" - {
        Service
          .generate(annotations, "v1", clusterIp = None, CanaryDeploymentType, JsonTransform.jq(JsonTransformExpression(".jqTest = \"test\"")), None, None)
          .toOption
          .get
          .get
          .payload
          .map(j => assert((j.hcursor --\ "jqTest").focus.contains(jString("test"))))
      }

      "options" - {
        "not defined" - {
          val generatedJson = Service.generate(annotations, "v1", clusterIp = None, CanaryDeploymentType, JsonTransform.noop, None, None).toOption.get
          val expectedJson =
            """
              |{
              |  "apiVersion": "v1",
              |  "kind": "Service",
              |  "metadata": {
              |    "labels": {
              |      "appName": "friendimpl"
              |    },
              |    "name": "friendimpl",
              |    "namespace": "chirper"
              |  },
              |  "spec": {
              |    "ports": [
              |      {
              |        "name": "ep1",
              |        "port": 1234,
              |        "protocol": "TCP",
              |        "targetPort": 1234
              |      }
              |    ],
              |    "selector": {
              |      "appName": "friendimpl"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get
          assert(generatedJson.get == Service("friendimpl", expectedJson, JsonTransform.noop))
        }

        "defined" - {
          val generatedJson = Service.generate(annotations, "v1", clusterIp = Some("10.0.0.5"), CanaryDeploymentType, JsonTransform.noop, Some("10.0.0.1"), Some("NodePort")).toOption.get
          val expectedJson =
            """
              |{
              |  "apiVersion": "v1",
              |  "kind": "Service",
              |  "metadata": {
              |    "labels": {
              |      "appName": "friendimpl"
              |    },
              |    "name": "friendimpl",
              |    "namespace": "chirper"
              |  },
              |  "spec": {
              |    "loadBalancerIP": "10.0.0.1",
              |    "clusterIP": "10.0.0.5",
              |    "ports": [
              |      {
              |        "name": "ep1",
              |        "port": 1234,
              |        "protocol": "TCP",
              |        "targetPort": 1234
              |      }
              |    ],
              |    "type": "NodePort",
              |    "selector": {
              |      "appName": "friendimpl"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get

          assert(generatedJson.get == Service("friendimpl", expectedJson, JsonTransform.noop))
        }
      }
    }
  }
}
