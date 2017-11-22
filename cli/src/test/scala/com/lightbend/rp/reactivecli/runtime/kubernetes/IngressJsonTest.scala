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
    appName = Some("friendimpl"),
    diskSpace = Some(65536L),
    memory = Some(8192L),
    nrOfCpus = Some(0.5D),
    endpoints = Map(
      "ep1" -> HttpEndpoint(
        index = 0,
        name = "ep1",
        port = 1234,
        version = Some(1),
        ingress = Seq(
          HttpIngress(Seq(80, 443), Seq.empty, Seq("/api/friend")),
          HttpIngress(Seq(80, 443), Seq("hello.com"), Seq.empty),
          HttpIngress(Seq(80, 443), Seq("hello.com", "world.io"), Seq("/api/friend", "/api/enemy"))))),
    secrets = Seq.empty,
    volumes = Map(
      "/my/guest/path/1" -> HostPathVolume("/my/host/path")),
    privileged = true,
    healthCheck = None,
    readinessCheck = None,
    environmentVariables = Map(
      "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
    version = Some(Version(3, 2, 1, Some("SNAPSHOT"))))

  val tests = this{
    "json serialization" - {
      "without additional arguments" - {
        val generatedJson = Ingress.generate(
          annotations,
          ingressAnnotations = Map.empty,
          pathAppend = Option.empty).get
        val expectedJson =
          """
            |{
            |  "apiVersion" : "extensions/v1beta1",
            |  "kind" : "Ingress",
            |  "metadata" : {
            |    "name" : "friendimpl",
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
            |                "serviceName" : "ep1-v1",
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
            |                "serviceName" : "ep1-v1",
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
            |                "serviceName" : "ep1-v1",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy",
            |              "backend" : {
            |                "serviceName" : "ep1-v1",
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
            |                "serviceName" : "ep1-v1",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy",
            |              "backend" : {
            |                "serviceName" : "ep1-v1",
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

        assert(generatedJson == Ingress("friendimpl", expectedJson))
      }

      "with ingress specific input" - {
        val generatedJson = Ingress.generate(
          annotations,
          ingressAnnotations = Map("kubernetes.io/ingress.class" -> "istio"),
          pathAppend = Some(".*")).get

        val expectedJson =
          """
            |{
            |  "apiVersion" : "extensions/v1beta1",
            |  "kind" : "Ingress",
            |  "metadata" : {
            |    "name" : "friendimpl",
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
            |                "serviceName" : "ep1-v1",
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
            |                "serviceName" : "ep1-v1",
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
            |                "serviceName" : "ep1-v1",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy.*",
            |              "backend" : {
            |                "serviceName" : "ep1-v1",
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
            |                "serviceName" : "ep1-v1",
            |                "servicePort" : 1234
            |              }
            |            },
            |            {
            |              "path" : "/api/enemy.*",
            |              "backend" : {
            |                "serviceName" : "ep1-v1",
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

        assert(generatedJson == Ingress("friendimpl", expectedJson))
      }

      "should fail if application name is not defined" - {
        assert(Ingress.generate(annotations.copy(appName = None), Map.empty, Option.empty).isFailure)
      }

    }

  }
}
