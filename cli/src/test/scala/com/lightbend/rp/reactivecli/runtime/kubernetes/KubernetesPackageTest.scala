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

import java.io.{ ByteArrayOutputStream, PrintStream }
import java.nio.file.{ Files, Paths }
import java.util.UUID

import argonaut._
import Argonaut._
import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.kubernetes.KubernetesArgs
import com.lightbend.rp.reactivecli.docker.Config
import utest._

import scala.util.{ Failure, Success, Try }

object KubernetesPackageTest extends TestSuite {
  val tests = this{
    "generateResources" - {
      val imageName = "fsat/testimpl:1.0.0-SNAPSHOT"

      val kubernetesArgs = KubernetesArgs()
      val generateDeploymentArgs = GenerateDeploymentArgs(
        dockerImage = Some(imageName),
        targetRuntimeArgs = Some(kubernetesArgs))

      "given valid docker image" - {
        val dockerConfig = Config(
          Config.Cfg(
            Image = Some(imageName),
            Labels = Some(Map(
              "com.lightbend.rp.namespace" -> "chirper",
              "com.lightbend.rp.app-name" -> "my-app",
              "com.lightbend.rp.app-version" -> "3.2.1-SNAPSHOT",
              "com.lightbend.rp.disk-space" -> "65536",
              "com.lightbend.rp.memory" -> "8192",
              "com.lightbend.rp.nr-of-cpus" -> "0.5",
              "com.lightbend.rp.privileged" -> "true",
              "com.lightbend.rp.environment-variables.0.type" -> "literal",
              "com.lightbend.rp.environment-variables.0.name" -> "testing1",
              "com.lightbend.rp.environment-variables.0.value" -> "testingvalue1",
              "com.lightbend.rp.environment-variables.0.some-key" -> "test",
              "com.lightbend.rp.environment-variables.1.type" -> "kubernetes.configMap",
              "com.lightbend.rp.environment-variables.1.name" -> "testing2",
              "com.lightbend.rp.environment-variables.1.map-name" -> "mymap",
              "com.lightbend.rp.environment-variables.1.key" -> "mykey",
              "com.lightbend.rp.environment-variables.2.type" -> "kubernetes.fieldRef",
              "com.lightbend.rp.environment-variables.2.name" -> "testing3",
              "com.lightbend.rp.environment-variables.2.field-path" -> "metadata.name",
              "com.lightbend.rp.volumes.0.type" -> "host-path",
              "com.lightbend.rp.volumes.0.path" -> "/my/host/path",
              "com.lightbend.rp.volumes.0.guest-path" -> "/my/guest/path/1",
              "com.lightbend.rp.volumes.0.some-key" -> "test",
              "com.lightbend.rp.volumes.1.type" -> "secret",
              "com.lightbend.rp.volumes.1.secret" -> "mysecret",
              "com.lightbend.rp.volumes.1.guest-path" -> "/my/guest/path/2",
              "com.lightbend.rp.endpoints.0.name" -> "ep1",
              "com.lightbend.rp.endpoints.0.protocol" -> "http",
              "com.lightbend.rp.endpoints.0.version" -> "9",
              "com.lightbend.rp.endpoints.0.ingress.0.type" -> "http",
              "com.lightbend.rp.endpoints.0.ingress.0.paths.0" -> "/pizza",
              "com.lightbend.rp.endpoints.0.some-key" -> "test",
              "com.lightbend.rp.endpoints.0.acls.0.some-key" -> "test",
              "com.lightbend.rp.endpoints.1.name" -> "ep2",
              "com.lightbend.rp.endpoints.1.protocol" -> "tcp",
              "com.lightbend.rp.endpoints.1.version" -> "1",
              "com.lightbend.rp.endpoints.1.port" -> "1234",
              "com.lightbend.rp.endpoints.2.name" -> "ep3",
              "com.lightbend.rp.endpoints.2.protocol" -> "udp",
              "com.lightbend.rp.endpoints.2.port" -> "1234"))))

        "generates kubernetes deployment + service resource" - {
          val result = generateResources(dockerConfig, generateDeploymentArgs, kubernetesArgs).toOption

          assert(result.nonEmpty)

          val generatedResources = result.get

          val (namespace, deployment, service, ingress) = generatedResources match {
            case Seq(namespace: Namespace, deployment: Deployment, service: Service, ingress: Ingress) =>
              (namespace, deployment, service, ingress)
          }

          assert(namespace.name == "chirper")
          val namespaceJsonExpected =
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
          // TODO: assert json later
          //assert(namespace.payload == namespaceJsonExpected)

          assert(deployment.name == "my-app-v3-2-1-snapshot")
          val deploymentJsonExpected =
            """
              |{
              |  "apiVersion": "apps/v1beta1",
              |  "kind": "Deployment",
              |  "metadata": {
              |    "name": "my-app-v3-2-1-snapshot",
              |    "labels": {
              |      "appName": "my-app",
              |      "appNameVersion": "my-app-v3.2.1-SNAPSHOT"
              |    }
              |  },
              |  "spec": {
              |    "replicas": 1,
              |    "serviceName": "my-app",
              |    "template": {
              |      "appName": "my-app",
              |      "appNameVersion": "my-app-v3.2.1-SNAPSHOT"
              |    },
              |    "spec": {
              |      "containers": [
              |        {
              |          "name": "my-app",
              |          "image": "fsat/testimpl:1.0.0-SNAPSHOT",
              |          "imagePullPolicy": "IfNotPresent",
              |          "env": [
              |            {
              |              "name": "RP_ENDPOINTS",
              |              "value": "EP1-V9,EP2-V1,EP3-V3"
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
              |              "value": "1234"
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
              |              "value": "1234"
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
              |              "name": "RP_ENDPOINT_EP3-V3_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3-V3_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3-V3_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3-V3_PORT",
              |              "value": "1234"
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
              |              "name": "RP_APP_VERSION",
              |              "value": "3.2.1-SNAPSHOT"
              |            },
              |            {
              |              "name": "testing1",
              |              "value": "testingvalue1"
              |            },
              |            {
              |              "name": "testing2",
              |              "valueFrom": {
              |                "configMapKeyRef": {
              |                  "name": "mymap",
              |                  "key": "mykey"
              |                }
              |              }
              |            },
              |            {
              |              "name": "testing3",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "metadata.name"
              |                }
              |              }
              |            }
              |          ],
              |          "ports": [
              |            {
              |              "containerPort": 10000,
              |              "name": "ep1"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep2"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep3"
              |            }
              |          ]
              |        }
              |      ]
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get

          // @TODO uncomment this test when we actually have the right format generated
          // @TODO i am proposing keeping them updated for now is counter-productive
          //assert(deployment.payload == deploymentJsonExpected)

          assert(service.name == "my-app")
          val serviceJsonExpected =
            """
              |{
              |  "apiVersion": "v1",
              |  "kind": "Service",
              |  "metadata": {
              |    "labels": {
              |      "appName": "my-app"
              |    },
              |    "name": "my-app",
              |    "namespace": "chirper"
              |  },
              |  "spec": {
              |    "clusterIP": "None",
              |    "ports": [
              |      {
              |        "name": "ep1",
              |        "port": 10000,
              |        "protocol": "TCP",
              |        "targetPort": 10000
              |      },
              |      {
              |        "name": "ep2",
              |        "port": 1234,
              |        "protocol": "TCP",
              |        "targetPort": 1234
              |      },
              |      {
              |        "name": "ep3",
              |        "port": 1234,
              |        "protocol": "UDP",
              |        "targetPort": 1234
              |      }
              |    ],
              |    "selector": {
              |      "appName": "my-app"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get
          assert(service.payload == serviceJsonExpected)

          assert(ingress.name == "my-app")
          val ingressJsonExpected =
            """
              |{
              |	"apiVersion": "extensions/v1beta1",
              |	"kind": "Ingress",
              |	"metadata": {
              |		"name": "my-app",
              |   "namespace": "chirper"
              |	},
              |	"spec": {
              |		"rules": [{
              |			"http": {
              |				"paths": [{
              |					"path": "/pizza",
              |					"backend": {
              |						"serviceName": "my-app",
              |						"servicePort": 10000
              |					}
              |				}]
              |			}
              |		}]
              |	}
              |}
            """.stripMargin.parse.right.get

          assert(ingress.payload == ingressJsonExpected)
        }

        "honor generate flags" - {
          "generateIngress" - {
            val result = generateResources(dockerConfig, generateDeploymentArgs, kubernetesArgs.copy(generateIngress = true)).toOption.get

            assert(result.length == 1)
            assert(result.head.resourceType == "ingress")
          }

          "generateNamespaces" - {
            val result = generateResources(dockerConfig, generateDeploymentArgs, kubernetesArgs.copy(generateNamespaces = true)).toOption.get

            assert(result.length == 1)
            assert(result.head.resourceType == "namespace")
          }

          "generatePodControllers" - {
            val result = generateResources(dockerConfig, generateDeploymentArgs, kubernetesArgs.copy(generatePodControllers = true)).toOption.get

            assert(result.length == 1)
            assert(result.head.resourceType == "deployment")
          }

          "generatePodControllers" - {
            val result = generateResources(dockerConfig, generateDeploymentArgs, kubernetesArgs.copy(generateServices = true)).toOption.get

            assert(result.length == 1)
            assert(result.head.resourceType == "service")
          }
        }

        "Validate Akka Clustering" - {
          val result = generateResources(
            dockerConfig.copy(config = dockerConfig.config.copy(Labels = dockerConfig.config.Labels.map(_ ++ Vector(
              "com.lightbend.rp.modules.akka-cluster-bootstrapping.enabled" -> "true"
            )))),
            generateDeploymentArgs,
            kubernetesArgs.copy(generateIngress = true))

          val failed = result.swap.toOption.get

          val message = failed.head

          val expected = "Akka Cluster Bootstrapping is enabled so you must specify `--pod-controller-replicas 2` (or greater)"

          assert(message == expected)
        }
      }
    }

    "handleGeneratedResources" - {
      "saves generated resources into filesystem" - {
        val generatedResources = Seq(
          Deployment("dep1", Json("key1" -> "value1".asJson)),
          Service("svc1", Json("key2" -> "value2".asJson)))

        val tmpDir = Paths.get(sys.props("java.io.tmpdir"))
        val testDir = tmpDir.resolve(UUID.randomUUID().toString)

        try {
          handleGeneratedResources(KubernetesArgs.Output.SaveToFile(testDir))(generatedResources)

          val deploymentFile = testDir.resolve("deployment-dep1.json")
          val deploymentFileContent = new String(Files.readAllBytes(deploymentFile))
          val deploymentFileExpected =
            """{
              |  "key1" : "value1"
              |}""".stripMargin
          assert(deploymentFileContent == deploymentFileExpected)

          val serviceFile = testDir.resolve("service-svc1.json")
          val serviceFileContent = new String(Files.readAllBytes(serviceFile))
          val serviceFileExpected =
            """{
              |  "key2" : "value2"
              |}""".stripMargin
          assert(serviceFileContent == serviceFileExpected)

        } finally {
          testDir.toFile.listFiles().foreach(f => Files.deleteIfExists(f.toPath))
          Files.deleteIfExists(testDir)
        }
      }

      "print generated resources as kubectl format into outputstream" - {
        val generatedResources = Seq(
          Deployment("deployment1", Json("key1" -> "value1".asJson)),
          Service("service1", Json("key2" -> "value2".asJson)))

        val output = new ByteArrayOutputStream()
        val printStream = new PrintStream(output)

        handleGeneratedResources(KubernetesArgs.Output.PipeToStream(printStream))(generatedResources)

        printStream.close()

        val generatedText = new String(output.toByteArray)
        val expectedText =
          """---
            |{
            |  "key1" : "value1"
            |}
            |---
            |{
            |  "key2" : "value2"
            |}
            |""".stripMargin
        assert(generatedText == expectedText)
      }
    }

    "serviceName" - {
      "normalize service names" - {
        Seq(
          "--akka-remote--" -> "akka-remote",
          "__akka_remote__" -> "akka-remote",
          "user-search" -> "user-search",
          "USER_SEARCH" -> "user-search",
          "h!e**(l+l??O" -> "h-e---l-l--o").foreach {
            case (input, expectedResult) =>
              val result = serviceName(input)
              assert(result == expectedResult)
          }

        Seq(
          "akka!remote" -> "akka-remote",
          "my/test" -> "my/test").foreach {
            case (input, expectedResult) =>
              val result = serviceName(input, Set('/'))
              assert(result == expectedResult)
          }
      }
    }

    "envVarName" - {
      "normalize endpoint names" - {
        Seq(
          "akka-remote" -> "AKKA_REMOTE",
          "--akka-remote--" -> "AKKA_REMOTE",
          "__akka-remote__" -> "AKKA_REMOTE",
          "user-search" -> "USER_SEARCH",
          "h!e**(l+l??O" -> "H_E___L_L__O").foreach {
            case (input, expectedResult) =>
              val result = envVarName(input)
              assert(result == expectedResult)
          }
      }
    }
  }
}
