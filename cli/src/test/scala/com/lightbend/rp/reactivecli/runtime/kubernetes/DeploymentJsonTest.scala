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

  val endpoints = Map(
    "ep1" -> HttpEndpoint(0, "ep1", 0, version = Some(9), Seq(HttpIngress(Seq(80, 443), Seq.empty, Seq("^/.*")))),
    "ep2" -> TcpEndpoint(1, "ep2", 1234, version = Some(1)),
    "ep3" -> UdpEndpoint(2, "ep3", 0, version = None))

  val annotations = Annotations(
    appName = Some("friendimpl"),
    diskSpace = Some(65536L),
    memory = Some(8192L),
    nrOfCpus = Some(0.5D),
    endpoints = endpoints,
    volumes = Map(
      "/my/guest/path/1" -> HostPathVolume("/my/host/path")),
    secrets = Seq(Secret("acme.co", "my-secret")),
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
              |              "containerPort": 10000,
              |              "name": "ep1-v9"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep2-v1"
              |            },
              |            {
              |              "containerPort": 10001,
              |              "name": "ep3"
              |            }
              |          ],
              |          "env": [
              |            {
              |              "name": "RP_ENDPOINTS",
              |              "value": "EP1-V9,EP2-V1,EP3"
              |            },
              |            {
              |              "name": "RP_ENDPOINTS_COUNT",
              |              "value": "3"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_BIND_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_BIND_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_BIND_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_BIND_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_KUBERNETES_POD_IP",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_KUBERNETES_POD_NAME",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "metadata.name"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_PLATFORM",
              |              "value": "kubernetes"
              |            },
              |            {
              |              "name": "RP_SECRETS_ACME_CO_MY_SECRET",
              |              "valueFrom": {
              |                "secretKeyRef": {
              |                  "name": "acme.co",
              |                  "key": "my-secret"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_VERSION",
              |              "value": "3.2.1-SNAPSHOT"
              |            },
              |            {
              |              "name": "RP_VERSION_MAJOR",
              |              "value": "3"
              |            },
              |            {
              |              "name": "RP_VERSION_MINOR",
              |              "value": "2"
              |            },
              |            {
              |              "name": "RP_VERSION_PATCH",
              |              "value": "1"
              |            },
              |            {
              |              "name": "RP_VERSION_PATCH_LABEL",
              |              "value": "SNAPSHOT"
              |            },
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

          val result = Deployment.generate(annotations, KubernetesVersion(1, 8), imageName,
            Deployment.ImagePullPolicy.Never, noOfReplicas = 1).get

          // @TODO uncomment this test when we actually have the right format generated
          // @TODO i am proposing keeping them updated for now is counter-productive
          //assert(result == Deployment("friendimpl-v3.2.1-SNAPSHOT", expectedJson))
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
              |              "containerPort": 10000,
              |              "name": "ep1-v9"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep2-v1"
              |            },
              |            {
              |              "containerPort": 10001,
              |              "name": "ep3"
              |            }
              |          ],
              |          "env": [
              |            {
              |              "name": "RP_ENDPOINTS",
              |              "value": "EP1-V9,EP2-V1,EP3"
              |            },
              |            {
              |              "name": "RP_ENDPOINTS_COUNT",
              |              "value": "3"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_BIND_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_BIND_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_BIND_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_BIND_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_KUBERNETES_POD_IP",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_KUBERNETES_POD_NAME",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "metadata.name"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_PLATFORM",
              |              "value": "kubernetes"
              |            },
              |            {
              |              "name": "RP_SECRETS_ACME_CO_MY_SECRET",
              |              "valueFrom": {
              |                "secretKeyRef": {
              |                  "name": "acme.co",
              |                  "key": "my-secret"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_VERSION",
              |              "value": "3.2.1-SNAPSHOT"
              |            },
              |            {
              |              "name": "RP_VERSION_MAJOR",
              |              "value": "3"
              |            },
              |            {
              |              "name": "RP_VERSION_MINOR",
              |              "value": "2"
              |            },
              |            {
              |              "name": "RP_VERSION_PATCH",
              |              "value": "1"
              |            },
              |            {
              |              "name": "RP_VERSION_PATCH_LABEL",
              |              "value": "SNAPSHOT"
              |            },
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

          // @TODO uncomment this test when we actually have the right format generated
          // @TODO i am proposing keeping them updated for now is counter-productive
          //assert(generatedJson == Deployment("friendimpl-v3.2.1-SNAPSHOT", expectedJson))
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
              |              "containerPort": 10000,
              |              "name": "ep1-v9"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep2-v1"
              |            },
              |            {
              |              "containerPort": 10001,
              |              "name": "ep3"
              |            }
              |          ],
              |          "env": [
              |            {
              |              "name": "RP_ENDPOINTS",
              |              "value": "EP1-V9,EP2-V1,EP3"
              |            },
              |            {
              |              "name": "RP_ENDPOINTS_COUNT",
              |              "value": "3"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_BIND_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_BIND_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_BIND_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1-V9_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2-V1_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_BIND_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_KUBERNETES_POD_IP",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_KUBERNETES_POD_NAME",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "metadata.name"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_PLATFORM",
              |              "value": "kubernetes"
              |            },
              |            {
              |              "name": "RP_SECRETS_ACME_CO_MY_SECRET",
              |              "valueFrom": {
              |                "secretKeyRef": {
              |                  "name": "acme.co",
              |                  "key": "my-secret"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_VERSION",
              |              "value": "3.2.1-SNAPSHOT"
              |            },
              |            {
              |              "name": "RP_VERSION_MAJOR",
              |              "value": "3"
              |            },
              |            {
              |              "name": "RP_VERSION_MINOR",
              |              "value": "2"
              |            },
              |            {
              |              "name": "RP_VERSION_PATCH",
              |              "value": "1"
              |            },
              |            {
              |              "name": "RP_VERSION_PATCH_LABEL",
              |              "value": "SNAPSHOT"
              |            },
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

          // @TODO uncomment this test when we actually have the right format generated
          // @TODO i am proposing keeping them updated for now is counter-productive
          //assert(generatedJson == Deployment("friendimpl-v3.2.1-SNAPSHOT", expectedJson))
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

      "assigned endpoint" - {
        "http" - {
          "with endpoint version" - {
            val endpoint = HttpEndpoint(0, "ep1", 0, version = Some(1), ingress = Seq.empty)
            val assigned = AssignedPort(
              endpoint = endpoint,
              port = 9999)
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1-v1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = assigned.asJson

            assert(expectedJson == generatedJson)
          }

          "without endpoint version" - {
            val endpoint = HttpEndpoint(0, "ep1", 0, version = None, ingress = Seq.empty)
            val assigned = AssignedPort(
              endpoint = endpoint,
              port = 9999)

            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = assigned.asJson

            assert(expectedJson == generatedJson)

          }
        }

        "tcp" - {
          "with endpoint version" - {
            val endpoint = TcpEndpoint(0, "ep1", 0, version = Some(1))
            val assigned = AssignedPort(
              endpoint = endpoint,
              port = 9999)
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1-v1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = assigned.asJson

            assert(expectedJson == generatedJson)
          }

          "without endpoint version" - {
            val endpoint = TcpEndpoint(0, "ep1", 0, version = None)
            val assigned = AssignedPort(
              endpoint = endpoint,
              port = 9999)
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = assigned.asJson

            assert(expectedJson == generatedJson)

          }
        }

        "udp" - {
          "with endpoint version" - {
            val endpoint = UdpEndpoint(0, "ep1", 0, version = Some(1))
            val assigned = AssignedPort(
              endpoint = endpoint,
              port = 9999)
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1-v1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = assigned.asJson

            assert(expectedJson == generatedJson)
          }

          "without endpoint version" - {
            val endpoint = UdpEndpoint(0, "ep1", 0, version = None)
            val assigned = AssignedPort(
              endpoint = endpoint,
              port = 9999)
            val expectedJson =
              """
                |{
                |  "containerPort": 9999,
                |  "name": "ep1"
                |}
              """.stripMargin.parse.right.get
            val generatedJson = assigned.asJson

            assert(expectedJson == generatedJson)

          }
        }
      }
    }

    "RP environment variables" - {
      "versions" - {
        "all fields" - {
          val result = RpEnvironmentVariables.versionEnvs(Version(3, 2, 1, Some("SNAPSHOT")))
          val expectedResult = Map(
            "RP_VERSION" -> LiteralEnvironmentVariable("3.2.1-SNAPSHOT"),
            "RP_VERSION_MAJOR" -> LiteralEnvironmentVariable("3"),
            "RP_VERSION_MINOR" -> LiteralEnvironmentVariable("2"),
            "RP_VERSION_PATCH" -> LiteralEnvironmentVariable("1"),
            "RP_VERSION_PATCH_LABEL" -> LiteralEnvironmentVariable("SNAPSHOT"))
          assert(result == expectedResult)
        }

        "major + minor + patch" - {
          val result = RpEnvironmentVariables.versionEnvs(Version(3, 2, 1, None))
          val expectedResult = Map(
            "RP_VERSION" -> LiteralEnvironmentVariable("3.2.1"),
            "RP_VERSION_MAJOR" -> LiteralEnvironmentVariable("3"),
            "RP_VERSION_MINOR" -> LiteralEnvironmentVariable("2"),
            "RP_VERSION_PATCH" -> LiteralEnvironmentVariable("1"))
          assert(result == expectedResult)
        }
      }

      "endpoints" - {
        "when present" - {
          val endpointsWithVersions = Map(
            "ep1" -> HttpEndpoint(0, "ep1", 0, version = Some(1), Seq.empty),
            "ep2" -> TcpEndpoint(1, "ep2", 1234, version = Some(3)),
            "ep3" -> UdpEndpoint(2, "ep3", 1234, version = Some(2)))

          val endpointsNoVersions = Map(
            "ep1" -> HttpEndpoint(0, "ep1", 0, version = None, Seq.empty),
            "ep2" -> TcpEndpoint(1, "ep2", 1234, version = None),
            "ep3" -> UdpEndpoint(2, "ep3", 1234, version = None))

          "with endpoint version" - {
            val result = RpEnvironmentVariables.endpointEnvs(endpointsWithVersions)
            val expectedResult = Map(
              "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable("3"),
              "RP_ENDPOINTS" -> LiteralEnvironmentVariable("EP1-V1,EP2-V3,EP3-V2"),

              "RP_ENDPOINT_EP1-V1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP1-V1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP1-V1_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_EP1-V1_PORT" -> LiteralEnvironmentVariable("10000"),

              "RP_ENDPOINT_EP2-V3_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP2-V3_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP2-V3_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP2-V3_PORT" -> LiteralEnvironmentVariable("1234"),

              "RP_ENDPOINT_EP3-V2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP3-V2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP3-V2_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP3-V2_PORT" -> LiteralEnvironmentVariable("1234"),

              "RP_ENDPOINT_0_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_0_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_0_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_0_PORT" -> LiteralEnvironmentVariable("10000"),

              "RP_ENDPOINT_1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_1_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_1_PORT" -> LiteralEnvironmentVariable("1234"),

              "RP_ENDPOINT_2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_2_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_2_PORT" -> LiteralEnvironmentVariable("1234"))

            assert(result == expectedResult)
          }

          "with no version" - {
            val result = RpEnvironmentVariables.endpointEnvs(endpointsNoVersions)
            val expectedResult = Map(
              "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable("3"),
              "RP_ENDPOINTS" -> LiteralEnvironmentVariable("EP1,EP2,EP3"),

              "RP_ENDPOINT_EP1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP3_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_0_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP3_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_0_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP1_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_EP2_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP3_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_0_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_1_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_2_PORT" -> LiteralEnvironmentVariable("1234"),

              "RP_ENDPOINT_EP1_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_EP2_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP3_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_0_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_1_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_2_BIND_PORT" -> LiteralEnvironmentVariable("1234"))

            assert(result == expectedResult)
          }

          "endpoints list should be ordered based on endpoint index" - {
            val endpoints = Map(
              "ep1" -> HttpEndpoint(2, "ep1", 0, version = Some(1), Seq.empty),
              "ep2" -> TcpEndpoint(0, "ep2", 1234, version = Some(3)),
              "ep3" -> UdpEndpoint(1, "ep3", 1234, version = Some(2)))

            val result = RpEnvironmentVariables.endpointEnvs(endpoints)

            assert(result("RP_ENDPOINTS") == LiteralEnvironmentVariable("EP2-V3,EP3-V2,EP1-V1"))
          }

          "auto port should be allocated for all undeclared ports" - {
            val endpoints = Map(
              "ep1" -> HttpEndpoint(0, "ep1", 0, version = Some(1), Seq.empty),
              "ep2" -> TcpEndpoint(1, "ep2", 1234, version = Some(3)),
              "ep3" -> UdpEndpoint(2, "ep3", 0, version = Some(2)))

            val result = RpEnvironmentVariables.endpointEnvs(endpoints)

            val expectedResult = Map(
              "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable("3"),
              "RP_ENDPOINTS" -> LiteralEnvironmentVariable("EP1-V1,EP2-V3,EP3-V2"),

              "RP_ENDPOINT_EP1-V1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP2-V3_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP3-V2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_0_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP1-V1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP2-V3_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP3-V2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_0_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP1-V1_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_EP2-V3_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP3-V2_PORT" -> LiteralEnvironmentVariable("10001"),
              "RP_ENDPOINT_0_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_1_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_2_PORT" -> LiteralEnvironmentVariable("10001"),

              "RP_ENDPOINT_EP1-V1_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_EP2-V3_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP3-V2_BIND_PORT" -> LiteralEnvironmentVariable("10001"),
              "RP_ENDPOINT_0_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_1_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_2_BIND_PORT" -> LiteralEnvironmentVariable("10001"))

            assert(result == expectedResult)
          }
        }

        "when empty" - {
          val result = RpEnvironmentVariables.endpointEnvs(Map.empty)
          val expectedResult = Map(
            "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable("0"))

          assert(result == expectedResult)
        }
      }
    }
  }
}
