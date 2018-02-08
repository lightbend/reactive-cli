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
import com.lightbend.rp.reactivecli.concurrent._
import scala.collection.immutable.Seq
import utest._

import Argonaut._

object IngressJsonTest extends TestSuite {
  def createAnnotations(appName: String, urlOne: String, urlTwo: String) =
    Annotations(
      namespace = Some("chirper"),
      applications = Vector.empty,
      appName = Some(appName),
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
            HttpIngress(Seq(80, 443), Seq.empty, Seq(urlOne)),
            HttpIngress(Seq(80, 443), Seq("hello.com"), Seq.empty),
            HttpIngress(Seq(80, 443), Seq("hello.com", "world.io"), Seq(urlOne, urlTwo))))),
      secrets = Seq.empty,
      privileged = true,
      environmentVariables = Map(
        "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
      version = Some("3.2.1-SNAPSHOT"),
      modules = Set.empty,
      akkaClusterBootstrapSystemName = None)

  val annotations = createAnnotations("friendservice", "/api/friend/", "/api/enemy")

  val tests = this{
    "json serialization" - {
      "without additional arguments" - {
        val generatedJson = Ingress.generate(
          annotations,
          "extensions/v1beta1",
          None,
          ingressAnnotations = Map.empty,
          None,
          None,
          pathAppend = Option.empty).toOption.get
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
            |              "path" : "/api/friend/",
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
            |              "path" : "/api/friend/",
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
            |            },
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
            |        "host" : "world.io",
            |        "http" : {
            |          "paths" : [
            |            {
            |              "path" : "/api/friend/",
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

        assert(generatedJson.get.json == expectedJson)
      }

      "with ingress specific input" - {
        val generatedJson = Ingress.generate(
          annotations,
          "extensions/v1beta1",
          Some(Vector("test.com")),
          ingressAnnotations = Map("kubernetes.io/ingress.class" -> "istio"),
          None,
          None,
          pathAppend = Some("/.*")).toOption.get

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
            |        "host" : "test.com",
            |        "http" : {
            |          "paths" : [
            |            {
            |              "path" : "/api/friend/.*",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy/.*",
            |              "backend" : {
            |                "serviceName" : "friendservice",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
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

        assert(generatedJson.get.json == expectedJson)
      }

      "should fail if application name is not defined" - {
        assert(Ingress.generate(annotations.copy(appName = None), "extensions/v1beta1", None, Map.empty, None, None, Option.empty).toOption.isEmpty)
      }

      "jq" - {
        Ingress
          .generate(annotations.copy(appName = Some("test")), "extensions/v1beta1", None, Map.empty, Some(".jqTest = \"test\""), None, Option.empty)
          .toOption
          .get
          .get
          .payload
          .map(j => assert((j.hcursor --\ "jqTest").focus.contains(jString("test"))))
      }

      "merge" - {
        val a = Ingress.generate(
          annotations,
          "extensions/v1beta1",
          Some(Vector("test.com")),
          ingressAnnotations = Map("kubernetes.io/ingress.class" -> "istio"),
          None,
          None,
          None).toOption.get.get

        val b = Ingress.generate(
          createAnnotations("enemyservice", "/ac/other/path", "/ab/other/path"),
          "extensions/v1beta1",
          Some(Vector("test.com")),
          ingressAnnotations = Map("kubernetes.io/ingress.class" -> "istio2"),
          None,
          None,
          None).toOption.get.get

        val expectedJson =
          """
            {
              "apiVersion" : "extensions/v1beta1",
              "kind" : "Ingress",
              "metadata" : {
                "name" : "testing",
                "annotations" : {
                  "kubernetes.io/ingress.class" : "istio2"
                },
                "namespace" : "chirper"
              },
              "spec" : {
                "rules" : [
                  {
                    "host" : "test.com",
                    "http" : {
                      "paths" : [
                        {
                          "path" : "/ab/other/path",
                          "backend" : {
                            "serviceName" : "enemyservice",
                            "servicePort" : 1234
                          }
                        },
                        {
                          "path" : "/ac/other/path",
                          "backend" : {
                            "serviceName" : "enemyservice",
                            "servicePort" : 1234
                          }
                        },
                        {
                          "path" : "/api/friend/",
                          "backend" : {
                            "serviceName" : "friendservice",
                            "servicePort" : 1234
                          }
                        },
                        {
                          "path" : "/api/enemy",
                          "backend" : {
                            "serviceName" : "friendservice",
                            "servicePort" : 1234
                          }
                        },
                        {
                          "backend" : {
                            "serviceName" : "enemyservice",
                            "servicePort" : 1234
                          }
                        },
                        {
                          "backend" : {
                            "serviceName" : "friendservice",
                            "servicePort" : 1234
                          }
                        }
                      ]
                    }
                  }
                ]
              }
            }
          """.parse.right.get.spaces2

        val generatedJson = Ingress.merge("testing", a, b).json.spaces2

        assert(generatedJson == expectedJson)
      }
    }
  }
}
