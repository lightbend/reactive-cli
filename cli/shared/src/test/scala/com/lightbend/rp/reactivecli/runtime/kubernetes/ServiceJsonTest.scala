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
import com.lightbend.rp.reactivecli.argparse.{ CanaryDeploymentType, BlueGreenDeploymentType, RollingDeploymentType, DiscoveryMethod }
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
      "remoting" -> TcpEndpoint(0, "remoting", 2552),
      "management" -> TcpEndpoint(1, "management", 8558),
      "ep3" -> TcpEndpoint(2, "ep3", 1234)),
    managementEndpointName = Some("management"),
    remotingEndpointName = Some("remoting"),
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
        val result = Service.generate(annotations.copy(endpoints = Map.empty), "v1", clusterIp = None, CanaryDeploymentType, DiscoveryMethod.AkkaDns, JsonTransform.noop, None, None).toOption.get.isEmpty

        assert(result)
      }

      "deploymentType" - {
        "Canary" - {
          Service
            .generate(annotations, "v1", clusterIp = None, CanaryDeploymentType, DiscoveryMethod.KubernetesApi, JsonTransform.noop, None, None)
            .toOption
            .get
            .head
            .payload
            .map { j =>
              val result = (j.hcursor --\ "spec" --\ "selector" --\ "app").focus
              val expected = Some(jString("friendimpl"))

              assert(result == expected)
            }
        }

        "BlueGreen" - {
          Service
            .generate(annotations, "v1", clusterIp = None, BlueGreenDeploymentType, DiscoveryMethod.KubernetesApi, JsonTransform.noop, None, None)
            .toOption
            .get
            .head
            .payload
            .map(j => assert((j.hcursor --\ "spec" --\ "selector" --\ "appNameVersion").focus.contains(jString("friendimpl-v3-2-1-snapshot"))))
        }

        "Rolling" - {
          Service
            .generate(annotations, "v1", clusterIp = None, RollingDeploymentType, DiscoveryMethod.KubernetesApi, JsonTransform.noop, None, None)
            .toOption
            .get
            .head
            .payload
            .map(j => assert((j.hcursor --\ "spec" --\ "selector" --\ "app").focus.contains(jString("friendimpl"))))
        }
      }

      "jq" - {
        Service
          .generate(annotations, "v1", clusterIp = None, CanaryDeploymentType, DiscoveryMethod.KubernetesApi, JsonTransform.jq(JsonTransformExpression(".jqTest = \"test\"")), None, None)
          .toOption
          .get
          .head
          .payload
          .map(j => assert((j.hcursor --\ "jqTest").focus.contains(jString("test"))))
      }

      "discovery method" - {
        "Akka DNS" - {
          val generatedJson = Service.generate(annotations, "v1", clusterIp = None, CanaryDeploymentType, DiscoveryMethod.AkkaDns, JsonTransform.noop, None, None).toOption.get
          val headlessJson =
            """
              |{
              |  "apiVersion": "v1",
              |  "kind": "Service",
              |  "metadata": {
              |    "labels": {
              |      "app": "friendimpl"
              |    },
              |    "annotations": {
              |      "service.alpha.kubernetes.io/tolerate-unready-endpoints" : "true"
              |    },
              |    "name": "friendimpl-internal",
              |    "namespace": "chirper"
              |  },
              |  "spec": {
              |    "ports": [
              |      {
              |        "name": "remoting",
              |        "port": 2552,
              |        "protocol": "TCP",
              |        "targetPort": 2552
              |      },
              |      {
              |        "name": "management",
              |        "port": 8558,
              |        "protocol": "TCP",
              |        "targetPort": 8558
              |      }
              |    ],
              |    "selector": {
              |      "app": "friendimpl"
              |    },
              |    "clusterIP" : "None",
              |    "publishNotReadyAddresses" : true
              |  }
              |}
            """.stripMargin.parse.right.get
          val serviceJson =
            """
              |{
              |  "apiVersion": "v1",
              |  "kind": "Service",
              |  "metadata": {
              |    "labels": {
              |      "app": "friendimpl"
              |    },
              |    "name": "friendimpl",
              |    "namespace": "chirper"
              |  },
              |  "spec": {
              |    "ports": [
              |      {
              |        "name": "ep3",
              |        "port": 1234,
              |        "protocol": "TCP",
              |        "targetPort": 1234
              |      }
              |    ],
              |    "selector": {
              |      "app": "friendimpl"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get
          assert(generatedJson == List(
            Service("friendimpl-internal", headlessJson, JsonTransform.noop),
            Service("friendimpl", serviceJson, JsonTransform.noop)))

        }
      }

      "options" - {
        "not defined" - {
          val generatedJson = Service.generate(annotations, "v1", clusterIp = None, CanaryDeploymentType, DiscoveryMethod.KubernetesApi, JsonTransform.noop, None, None).toOption.get
          val expectedJson =
            """
              |{
              |  "apiVersion": "v1",
              |  "kind": "Service",
              |  "metadata": {
              |    "labels": {
              |      "app": "friendimpl"
              |    },
              |    "name": "friendimpl",
              |    "namespace": "chirper"
              |  },
              |  "spec": {
              |    "ports": [
              |      {
              |        "name": "remoting",
              |        "port": 2552,
              |        "protocol": "TCP",
              |        "targetPort": 2552
              |      },
              |      {
              |        "name": "management",
              |        "port": 8558,
              |        "protocol": "TCP",
              |        "targetPort": 8558
              |      },
              |      {
              |        "name": "ep3",
              |        "port": 1234,
              |        "protocol": "TCP",
              |        "targetPort": 1234
              |      }
              |    ],
              |    "selector": {
              |      "app": "friendimpl"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get
          assert(generatedJson == List(Service("friendimpl", expectedJson, JsonTransform.noop)))
        }

        "defined" - {
          val generatedJson = Service.generate(annotations, "v1", clusterIp = Some("10.0.0.5"), CanaryDeploymentType, DiscoveryMethod.KubernetesApi, JsonTransform.noop, Some("10.0.0.1"), Some("NodePort")).toOption.get
          val expectedJson =
            """
              |{
              |  "apiVersion": "v1",
              |  "kind": "Service",
              |  "metadata": {
              |    "labels": {
              |      "app": "friendimpl"
              |    },
              |    "name": "friendimpl",
              |    "namespace": "chirper"
              |  },
              |  "spec": {
              |    "loadBalancerIP": "10.0.0.1",
              |    "clusterIP": "10.0.0.5",
              |    "ports": [
              |      {
              |        "name": "remoting",
              |        "port": 2552,
              |        "protocol": "TCP",
              |        "targetPort": 2552
              |      },
              |      {
              |        "name": "management",
              |        "port": 8558,
              |        "protocol": "TCP",
              |        "targetPort": 8558
              |      },
              |      {
              |        "name": "ep3",
              |        "port": 1234,
              |        "protocol": "TCP",
              |        "targetPort": 1234
              |      }
              |    ],
              |    "type": "NodePort",
              |    "selector": {
              |      "app": "friendimpl"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get

          assert(generatedJson == List(Service("friendimpl", expectedJson, JsonTransform.noop)))
        }
      }
    }
  }
}
