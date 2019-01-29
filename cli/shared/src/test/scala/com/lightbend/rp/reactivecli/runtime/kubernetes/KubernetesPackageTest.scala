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
import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.kubernetes.KubernetesArgs
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.Config
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.json.JsonTransform
import com.lightbend.rp.reactivecli.runtime.GeneratedResource
import java.io.{ ByteArrayOutputStream, PrintStream }
import scala.collection.immutable.Seq
import scala.concurrent.Future
import utest._

import Argonaut._

object KubernetesPackageTest extends TestSuite {
  val tests = this{
    "generateResources" - {
      val imageName = "fsat/testimpl:1.0.0-SNAPSHOT"

      val kubernetesArgs = KubernetesArgs(
        generateIngress = true,
        generateNamespaces = false,
        generatePodControllers = true,
        generateServices = true)
      val generateDeploymentArgs = GenerateDeploymentArgs(
        dockerImages = Seq(imageName),
        targetRuntimeArgs = Some(kubernetesArgs))

      "given valid docker image" - {
        val dockerConfig = Config(
          Config.Cfg(
            Image = Some(imageName),
            Labels = Some(Map(
              "com.lightbend.rp.app-name" -> "my-app",
              "com.lightbend.rp.app-version" -> "3.2.1-SNAPSHOT",
              "com.lightbend.rp.disk-space" -> "65536",
              "com.lightbend.rp.memory" -> "8192",
              "com.lightbend.rp.cpu" -> "0.5",
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
          val k8sArgs = kubernetesArgs.copy(generateNamespaces = true, namespace = Some("chirper"))

          generateResources(imageName, dockerConfig, generateDeploymentArgs.copy(targetRuntimeArgs = Some(k8sArgs)), k8sArgs)
            .map(_.toOption)
            .flatMap { result =>
              assert(result.nonEmpty)

              val generatedResources = result.get

              val (namespace, deployment, service, ingress) = generatedResources match {
                case Seq(namespace: Namespace, deployment: Deployment, service: Service, ingress: Ingress) =>
                  (namespace, deployment, service, ingress)
              }

              var asserts = List.empty[Future[Unit]]
              def assertPayload(label: String, generatedResource: GeneratedResource[Json], jsonExpected: Json): Unit = {
                asserts ::= generatedResource.payload.map { p =>
                  if (p != jsonExpected)
                    println(s"$label payload:\n" + PrettyParams.spaces2.copy(colonLeft = "").pretty(p))
                  assert(p == jsonExpected)
                }
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
              assertPayload("namespace", namespace, namespaceJsonExpected)

              assert(deployment.name == "my-app-v3-2-1-snapshot")
              val deploymentJsonExpected =
                """
                  |{
                  |  "apiVersion": "apps/v1beta2",
                  |  "kind": "Deployment",
                  |  "metadata": {
                  |    "name": "my-app-v3-2-1-snapshot",
                  |    "labels": {
                  |      "appName": "my-app",
                  |      "appNameVersion": "my-app-v3-2-1-snapshot"
                  |    },
                  |    "namespace": "chirper"
                  |  },
                  |  "spec": {
                  |    "replicas": 1,
                  |    "selector": {
                  |      "matchLabels": {
                  |        "appNameVersion": "my-app-v3-2-1-snapshot"
                  |      }
                  |    },
                  |    "template": {
                  |      "metadata": {
                  |        "labels": {
                  |          "appName": "my-app",
                  |          "appNameVersion": "my-app-v3-2-1-snapshot"
                  |        }
                  |      },
                  |      "spec": {
                  |        "restartPolicy": "Always",
                  |        "containers": [
                  |          {
                  |            "name": "my-app",
                  |            "image": "fsat/testimpl:1.0.0-SNAPSHOT",
                  |            "resources": {
                  |              "limits": {
                  |                "cpu": 0.5,
                  |                "memory": 8192
                  |              },
                  |              "requests": {
                  |                "cpu": 0.5,
                  |                "memory": 8192
                  |              }
                  |            },
                  |            "imagePullPolicy": "IfNotPresent",
                  |            "volumeMounts": [],
                  |            "env": [
                  |              {
                  |                "name": "RP_APP_NAME",
                  |                "value": "my-app"
                  |              },
                  |              {
                  |                "name": "RP_APP_VERSION",
                  |                "value": "3.2.1-SNAPSHOT"
                  |              },
                  |              {
                  |                "name": "RP_KUBERNETES_POD_IP",
                  |                "valueFrom": {
                  |                  "fieldRef": {
                  |                    "fieldPath": "status.podIP"
                  |                  }
                  |                }
                  |              },
                  |              {
                  |                "name": "RP_KUBERNETES_POD_NAME",
                  |                "valueFrom": {
                  |                  "fieldRef": {
                  |                    "fieldPath": "metadata.name"
                  |                  }
                  |                }
                  |              },
                  |              {
                  |                "name": "RP_NAMESPACE",
                  |                "valueFrom": {
                  |                  "fieldRef": {
                  |                    "fieldPath": "metadata.namespace"
                  |                  }
                  |                }
                  |              },
                  |              {
                  |                "name": "RP_PLATFORM",
                  |                "value": "kubernetes"
                  |              },
                  |              {
                  |                "name": "testing1",
                  |                "value": "testingvalue1"
                  |              },
                  |              {
                  |                "name": "testing2",
                  |                "valueFrom": {
                  |                  "configMapKeyRef": {
                  |                    "name": "mymap",
                  |                    "key": "mykey"
                  |                  }
                  |                }
                  |              },
                  |              {
                  |                "name": "testing3",
                  |                "valueFrom": {
                  |                  "fieldRef": {
                  |                    "fieldPath": "metadata.name"
                  |                  }
                  |                }
                  |              }
                  |            ],
                  |            "ports": [
                  |              {
                  |                "containerPort": 10000,
                  |                "name": "ep1"
                  |              },
                  |              {
                  |                "containerPort": 1234,
                  |                "name": "ep2"
                  |              },
                  |              {
                  |                "containerPort": 1234,
                  |                "name": "ep3"
                  |              }
                  |            ]
                  |          }
                  |        ],
                  |        "volumes": []
                  |      }
                  |    }
                  |  }
                  |}
                """.stripMargin.parse.right.get
              assertPayload("deployment", deployment, deploymentJsonExpected)

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
              assertPayload("service", service, serviceJsonExpected)

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
              assertPayload("ingress", ingress, ingressJsonExpected)

              Future.sequence(asserts.reverse)
            }
        }

        "honor generate flags" - {
          "generateNamespaces" - {
            val k8sArgs = kubernetesArgs.copy(generateNamespaces = true, namespace = Some("test"))
            generateResources(imageName, dockerConfig, generateDeploymentArgs.copy(targetRuntimeArgs = Some(k8sArgs)), k8sArgs)
              .map(_.toOption.get)
              .map { result =>
                assert(result.length == 4)
                assert(result.head.resourceType == "namespace")
              }
          }

          "generateIngress" - {
            val k8sArgs = kubernetesArgs.copy(generateIngress = true, generatePodControllers = false, generateServices = false)
            generateResources(imageName, dockerConfig, generateDeploymentArgs.copy(targetRuntimeArgs = Some(k8sArgs)), k8sArgs)
              .map(_.toOption.get)
              .map { result =>
                assert(result.length == 1)
                assert(result.head.resourceType == "ingress")
              }
          }

          "generatePodControllers" - {
            val k8sArgs = kubernetesArgs.copy(generatePodControllers = true, generateIngress = false, generateServices = false)
            generateResources(imageName, dockerConfig, generateDeploymentArgs.copy(targetRuntimeArgs = Some(k8sArgs)), k8sArgs)
              .map(_.toOption.get)
              .map { result =>
                assert(result.length == 1)
                assert(result.head.resourceType == "deployment")
              }
          }

          "generateServices" - {
            val k8sArgs = kubernetesArgs.copy(generateServices = true, generatePodControllers = false, generateIngress = false)
            generateResources(imageName, dockerConfig, generateDeploymentArgs.copy(targetRuntimeArgs = Some(k8sArgs)), k8sArgs)
              .map(_.toOption.get)
              .map { result =>
                assert(result.length == 1)
                assert(result.head.resourceType == "service")
              }
          }
        }

        "Validate Akka Clustering" - {
          "Require 2 replicas by default" - {
            generateResources(
              imageName,
              dockerConfig.copy(config = dockerConfig.config.copy(Labels = dockerConfig.config.Labels.map(_ ++ Vector(
                "com.lightbend.rp.modules.akka-cluster-bootstrapping.enabled" -> "true")))),
              generateDeploymentArgs,
              kubernetesArgs.copy(generateIngress = true))
              .map { result =>
                val failed = result.swap.toOption.get

                val message = failed.head

                val expected = "Akka Cluster Bootstrapping is enabled so you must specify `--pod-controller-replicas 2` (or greater), or provide `--akka-cluster-join-existing` to only join already formed clusters"

                assert(message == expected)
              }
          }

          "Skip replica validation when only joining existing cluster" - {
            generateResources(
              imageName,
              dockerConfig.copy(config = dockerConfig.config.copy(Labels = dockerConfig.config.Labels.map(_ ++ Vector(
                "com.lightbend.rp.modules.akka-cluster-bootstrapping.enabled" -> "true")))),
              generateDeploymentArgs.copy(akkaClusterJoinExisting = true),
              kubernetesArgs.copy(generateIngress = true))
              .map { result =>
                assert(result.isSuccess)
              }
          }
        }
      }
    }

    "handleGeneratedResources" - {
      "saves generated resources into filesystem" - {
        val generatedResources = Seq(
          Deployment("dep1", Json("key1" -> "value1".asJson), JsonTransform.noop),
          Service("svc1", Json("key2" -> "value2".asJson), JsonTransform.noop))
        withTempDir { testDir =>
          handleGeneratedResources(KubernetesArgs.Output.SaveToFile(testDir))(generatedResources).map { _ =>
            val deploymentFile = pathFor(testDir, "deployment-dep1.yml")
            val deploymentFileContent = readFile(deploymentFile)
            val deploymentFileExpected =
              """key1: value1""".stripMargin
            assert(deploymentFileContent == deploymentFileExpected)

            val serviceFile = pathFor(testDir, "service-svc1.yml")
            val serviceFileContent = readFile(serviceFile)
            val serviceFileExpected =
              """key2: value2""".stripMargin
            assert(serviceFileContent == serviceFileExpected)
          }
        }
      }

      "print generated resources as kubectl format into outputstream" - {
        val generatedResources = Seq(
          Deployment("deployment1", Json("key1" -> "value1".asJson), JsonTransform.noop),
          Service("service1", Json("key2" -> "value2".asJson), JsonTransform.noop))

        val output = new ByteArrayOutputStream()
        val printStream = new PrintStream(output)

        handleGeneratedResources(KubernetesArgs.Output.PipeToStream(printStream))(generatedResources).map { _ =>
          printStream.close()

          val generatedText = new String(output.toByteArray)
          val expectedText =
            """|---
               |key1: value1
               |---
               |key2: value2
               |""".stripMargin
          assert(generatedText == expectedText)
        }
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
