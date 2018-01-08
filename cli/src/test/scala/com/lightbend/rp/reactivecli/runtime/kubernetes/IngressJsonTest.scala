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
import com.lightbend.rp.reactivecli.annotations._
import utest._

import scala.collection.immutable.Seq

object IngressJsonTest extends TestSuite {
  val annotations = Annotations(
    namespace = Some("chirper"),
    appName = Some("friendservice"),
    appType = None,
    configResource = None,
    diskSpace = Some(65536L),
    memory = Some(8192L),
    cpu = Some(0.5D),
    endpoints = Map(
      "ep1" -> HttpEndpoint(
        index = 0,
        name = "ep1",
        port = 1234,
        ingress = Seq(
          HttpIngress(Seq(80, 443), Seq.empty, Seq("/api/friend")),
          HttpIngress(Seq(80, 443), Seq("hello.com"), Seq.empty),
          HttpIngress(Seq(80, 443), Seq("hello.com", "world.io"), Seq("/api/friend", "/api/enemy"))))),
    secrets = Seq.empty,
    privileged = true,
    environmentVariables = Map(
      "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
    version = Some("3.2.1-SNAPSHOT"),
    modules = Set.empty,
    akkaClusterBootstrapSystemName = None)

  val tests = this{
    "json serialization" - {
      "without additional arguments" - {
        val generatedJson = Ingress.generate(
          annotations,
          "extensions/v1beta1",
          ingressAnnotations = Map.empty,
          pathAppend = Option.empty,
          None).toOption.get
        val expectedJson =
          """
            |{
            |  "apiVersion" : "extensions/v1beta1",
            |  "kind" : "Ingress",
            |  "metadata" : {
            |    "name" : "friendservice",
            |    "namespace": "chirper"
            |  },
            |  "spec" : {
            |    "rules" : [
            |      {
            |        "http" : {
            |          "paths" : [
            |            {
            |              "path" : "/api/friend",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            }
            |          ]
            |        }
            |      },
            |      {
            |        "host" : "hello.com",
            |        "http" : {
            |          "paths" : [
            |            {
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            }
            |          ]
            |        }
            |      },
            |      {
            |        "host" : "hello.com",
            |        "http" : {
            |          "paths" : [
            |            {
            |              "path" : "/api/friend",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            }
            |          ]
            |        }
            |      },
            |      {
            |        "host" : "world.io",
            |        "http" : {
            |          "paths" : [
            |            {
            |              "path" : "/api/friend",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            }
            |          ]
            |        }
            |      }
            |    ]
            |  }
            |}
          """.stripMargin.parse.right.get

        assert(generatedJson.contains(Ingress("friendservice", expectedJson, None)))
      }

      "with ingress specific input" - {
        val generatedJson = Ingress.generate(
          annotations,
          "extensions/v1beta1",
          ingressAnnotations = Map("kubernetes.io/ingress.class" -> "istio"),
          pathAppend = Some(".*"),
          None).toOption.get

        val expectedJson =
          """
            |{
            |  "apiVersion" : "extensions/v1beta1",
            |  "kind" : "Ingress",
            |  "metadata" : {
            |    "name" : "friendservice",
            |    "annotations" : {
            |      "kubernetes.io/ingress.class" : "istio"
            |    },
            |    "namespace": "chirper"
            |  },
            |  "spec" : {
            |    "rules" : [
            |      {
            |        "http" : {
            |          "paths" : [
            |            {
            |              "path" : "/api/friend.*",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            }
            |          ]
            |        }
            |      },
            |      {
            |        "host" : "hello.com",
            |        "http" : {
            |          "paths" : [
            |            {
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            }
            |          ]
            |        }
            |      },
            |      {
            |        "host" : "hello.com",
            |        "http" : {
            |          "paths" : [
            |            {
            |              "path" : "/api/friend.*",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy.*",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            }
            |          ]
            |        }
            |      },
            |      {
            |        "host" : "world.io",
            |        "http" : {
            |          "paths" : [
            |            {
            |              "path" : "/api/friend.*",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy.*",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            }
            |          ]
            |        }
            |      }
            |    ]
            |  }
            |}
          """.stripMargin.parse.right.get

        assert(generatedJson.contains(Ingress("friendservice", expectedJson, None)))
      }

      "should fail if application name is not defined" - {
        assert(Ingress.generate(annotations.copy(appName = None), "extensions/v1beta1", Map.empty, Option.empty, None).toOption.isEmpty)
      }

      "jq" - {
        (Ingress.generate(annotations.copy(appName = Some("test")), "extensions/v1beta1", Map.empty, Option.empty, Some(".jqTest = \"test\"")).toOption.get.get.payload.hcursor --\ "jqTest")
          .focus
          .contains(jString("test"))
      }
    }

  }
}
