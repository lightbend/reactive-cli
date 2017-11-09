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
import com.lightbend.rp.reactivecli.annotations.kubernetes._
import com.lightbend.rp.reactivecli.annotations._
import utest._
import scala.collection.immutable.Seq

object DeploymentJsonTest extends TestSuite {
  import Deployment._

  val annotations = Annotations(
    appName = Some("friendimpl"),
    diskSpace = Some(65536L),
    memory = Some(8192L),
    nrOfCpus = Some(0.5D),
    endpoints = Map(
      "ep1" -> HttpEndpoint("ep1", 0, version = Some(9), Seq(HttpEndpoint.HttpAcl("^/.*"))),
      "ep2" -> TcpEndpoint("ep2", 1234, version = Some(1)),
      "ep3" -> UdpEndpoint("ep3", 1234, version = None)),
    volumes = Map(
      "/my/guest/path/1" -> HostPathVolume("/my/host/path"),
      "/my/guest/path/2" -> SecretVolume("mysecret")),
    privileged = true,
    healthCheck = None,
    readinessCheck = None,
    environmentVariables = Map(
      "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
    version = Some(Version(3, 2, 1, Some("SNAPSHOT"))))

  val imageName = "my-repo/my-image"

  val tests = this{
    "json serialization" - {
      "deployment" - {
        "K8 >= 1.8" - {
          val expectedJson =
            """
              |{
              |  "apiVersion": "apps/v1beta2",
              |  "kind": "Deployment",
              |  "metadata": {
              |    "labels": {
              |      "app": "friendimpl",
              |      "appVersionMajor": "friendimpl-v3",
              |      "appVersionMajorMinor": "friendimpl-v3.2",
              |      "appVersion": "friendimpl-v3.2.1-SNAPSHOT"
              |    },
              |    "name": "friendimpl-v3.2.1-SNAPSHOT"
              |  },
              |  "spec": {
              |    "replicas": 1,
              |    "serviceName": "friendimpl-v3",
              |    "template": {
              |      "app": "friendimpl",
              |      "appVersionMajor": "friendimpl-v3",
              |      "appVersionMajorMinor": "friendimpl-v3.2",
              |      "appVersion": "friendimpl-v3.2.1-SNAPSHOT"
              |    },
              |    "spec": {
              |      "containers": [
              |        {
              |          "name": "friendimpl",
              |          "image": "my-repo/my-image",
              |          "imagePullPolicy": "Never",
              |          "ports": [
              |            {
              |              "containerPort": 0,
              |              "name": "ep1-v9"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep2-v1"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep3"
              |            }
              |          ],
              |          "env": [
              |            {
              |              "name": "testing1",
              |              "value": "testingvalue1"
              |            }
              |          ]
              |        }
              |      ]
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get

          val generatedJson = Deployment.generate(annotations, KubernetesVersion(1, 8), imageName,
            Deployment.ImagePullPolicy.Never, noOfReplicas = 1).get
          assert(generatedJson == Deployment("friendimpl-v3.2.1-SNAPSHOT", expectedJson))
        }

        "K8 < 1.8" - {
          val expectedJson =
            """
              |{
              |  "apiVersion": "apps/v1beta1",
              |  "kind": "Deployment",
              |  "metadata": {
              |    "labels": {
              |      "app": "friendimpl",
              |      "appVersionMajor": "friendimpl-v3",
              |      "appVersionMajorMinor": "friendimpl-v3.2",
              |      "appVersion": "friendimpl-v3.2.1-SNAPSHOT"
              |    },
              |    "name": "friendimpl-v3.2.1-SNAPSHOT"
              |  },
              |  "spec": {
              |    "replicas": 1,
              |    "serviceName": "friendimpl-v3",
              |    "template": {
              |      "app": "friendimpl",
              |      "appVersionMajor": "friendimpl-v3",
              |      "appVersionMajorMinor": "friendimpl-v3.2",
              |      "appVersion": "friendimpl-v3.2.1-SNAPSHOT"
              |    },
              |    "spec": {
              |      "containers": [
              |        {
              |          "name": "friendimpl",
              |          "image": "my-repo/my-image",
              |          "imagePullPolicy": "Never",
              |          "ports": [
              |            {
              |              "containerPort": 0,
              |              "name": "ep1-v9"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep2-v1"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep3"
              |            }
              |          ],
              |          "env": [
              |            {
              |              "name": "testing1",
              |              "value": "testingvalue1"
              |            }
              |          ]
              |        }
              |      ]
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get

          val generatedJson = Deployment.generate(annotations, KubernetesVersion(1, 7), imageName,
            Deployment.ImagePullPolicy.Never, noOfReplicas = 1).get
          assert(generatedJson == Deployment("friendimpl-v3.2.1-SNAPSHOT", expectedJson))
        }

        "with checks" - {
          val expectedJson =
            """
              |{
              |  "apiVersion": "apps/v1beta2",
              |  "kind": "Deployment",
              |  "metadata": {
              |    "labels": {
              |      "app": "friendimpl",
              |      "appVersionMajor": "friendimpl-v3",
              |      "appVersionMajorMinor": "friendimpl-v3.2",
              |      "appVersion": "friendimpl-v3.2.1-SNAPSHOT"
              |    },
              |    "name": "friendimpl-v3.2.1-SNAPSHOT"
              |  },
              |  "spec": {
              |    "replicas": 1,
              |    "serviceName": "friendimpl-v3",
              |    "template": {
              |      "app": "friendimpl",
              |      "appVersionMajor": "friendimpl-v3",
              |      "appVersionMajorMinor": "friendimpl-v3.2",
              |      "appVersion": "friendimpl-v3.2.1-SNAPSHOT"
              |    },
              |    "spec": {
              |      "containers": [
              |        {
              |          "name": "friendimpl",
              |          "image": "my-repo/my-image",
              |          "imagePullPolicy": "Never",
              |          "ports": [
              |            {
              |              "containerPort": 0,
              |              "name": "ep1-v9"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep2-v1"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep3"
              |            }
              |          ],
              |          "env": [
              |            {
              |              "name": "testing1",
              |              "value": "testingvalue1"
              |            }
              |          ],
              |          "readinessProbe": {
              |            "exec": {
              |              "command": ["ls", "-al"]
              |            }
              |          },
              |          "livenessProbe": {
              |            "tcpSocket": {
              |              "port": 1234
              |            },
              |            "periodSeconds": 3
              |          }
              |        }
              |      ]
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get

          val input = annotations.copy(
            readinessCheck = Some(CommandCheck("ls", "-al")),
            healthCheck = Some(TcpCheck(Check.PortNumber(1234), intervalSeconds = 3)))
          val generatedJson = Deployment.generate(input, KubernetesVersion(1, 8), imageName,
            Deployment.ImagePullPolicy.Never, noOfReplicas = 1).get
          assert(generatedJson == Deployment("friendimpl-v3.2.1-SNAPSHOT", expectedJson))
        }

        "should fail if application name is not defined" - {
          val invalid = annotations.copy(appName = None)
          assert(Deployment.generate(invalid, KubernetesVersion(1, 7), imageName,
            Deployment.ImagePullPolicy.Never, 1).isFailure)
        }
      }

      "check" - {
        "command" - {
          val check = CommandCheck("ls", "-la")
          val expectedJson =
            """
              |{"exec": {"command": ["ls", "-la"]}}
            """.stripMargin.parse.right.get
          val generatedJson = check.asJson
          assert(generatedJson == expectedJson)
        }

        "tcp" - {
          "port number" - {
            val check = TcpCheck(Check.PortNumber(1234), intervalSeconds = 5)
            val expectedJson =
              """
                |{"tcpSocket": {"port": 1234}, "periodSeconds": 5}
              """.stripMargin.parse.right.get
            val generatedJson = check.asJson
            assert(generatedJson == expectedJson)
          }

          "service name" - {
            val check = TcpCheck(Check.ServiceName("service-ref"), intervalSeconds = 5)
            val expectedJson =
              """
                |{"tcpSocket": {"port": "service-ref"}, "periodSeconds": 5}
              """.stripMargin.parse.right.get
            val generatedJson = check.asJson
            assert(generatedJson == expectedJson)
          }
        }

        "http" - {
          "port number" - {
            val check = HttpCheck(Check.PortNumber(1234), intervalSeconds = 5, path = "/test")
            val expectedJson =
              """
                |{"httpGet": {"path": "/test","port": 1234}, "periodSeconds": 5}
              """.stripMargin.parse.right.get
            val generatedJson = check.asJson
            assert(generatedJson == expectedJson)
          }

          "service name" - {
            val check = HttpCheck(Check.ServiceName("service-ref"), intervalSeconds = 5, path = "/test")
            val expectedJson =
              """
                |{"httpGet": {"path": "/test", "port": "service-ref"}, "periodSeconds": 5}
              """.stripMargin.parse.right.get
            val generatedJson = check.asJson
            assert(generatedJson == expectedJson)
          }
        }
      }

      "environment" - {
        "literal" - {
          val env = LiteralEnvironmentVariable("hey")
          val expectedJson =
            """
              |{
              |  "value": "hey"
              |}
            """.stripMargin.parse.right.get
          val generatedJson = env.asJson

          assert(expectedJson == generatedJson)
        }

        "field ref" - {
          val env = FieldRefEnvironmentVariable("metadata.name")
          val expectedJson =
            """
              |{
              |  "valueFrom": {
              |    "fieldRef": {
              |      "fieldPath": "metadata.name"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get
          val generatedJson = env.asJson

          assert(expectedJson == generatedJson)
        }

        "config map" - {
          val env = ConfigMapEnvironmentVariable(mapName = "special-config", key = "s3.bucket")
          val expectedJson =
            """
              |{
              |  "valueFrom": {
              |    "configMapKeyRef": {
              |      "name": "special-config",
              |      "key": "s3.bucket"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get
          val generatedJson = env.asJson

          assert(expectedJson == generatedJson)
        }

      }

      "endpoint" - {
        "http" - {
          "with endpoint version" - {
            val endpoint = HttpEndpoint("ep1", 9999, version = Some(1), acls = Seq(HttpEndpoint.HttpAcl("/visualizer")))
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1-v1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = endpoint.asJson

            assert(expectedJson == generatedJson)
          }

          "without endpoint version" - {
            val endpoint = HttpEndpoint("ep1", 9999, version = None, acls = Seq(HttpEndpoint.HttpAcl("/visualizer")))
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = endpoint.asJson

            assert(expectedJson == generatedJson)

          }
        }

        "tcp" - {
          "with endpoint version" - {
            val endpoint = TcpEndpoint("ep1", 9999, version = Some(1))
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1-v1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = endpoint.asJson

            assert(expectedJson == generatedJson)
          }

          "without endpoint version" - {
            val endpoint = TcpEndpoint("ep1", 9999, version = None)
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = endpoint.asJson

            assert(expectedJson == generatedJson)

          }
        }

        "udp" - {
          "with endpoint version" - {
            val endpoint = UdpEndpoint("ep1", 9999, version = Some(1))
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1-v1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = endpoint.asJson

            assert(expectedJson == generatedJson)
          }

          "without endpoint version" - {
            val endpoint = UdpEndpoint("ep1", 9999, version = None)
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = endpoint.asJson

            assert(expectedJson == generatedJson)

          }
        }
      }
    }
  }
}
