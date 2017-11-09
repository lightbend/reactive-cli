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
import com.lightbend.rp.reactivecli.argparse.kubernetes.{ IngressArgs, KubernetesArgs }
import com.lightbend.rp.reactivecli.docker.Config
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment.KubernetesVersion
import utest._

import scala.util.{ Failure, Success, Try }

object KubernetesPackageTest extends TestSuite {
  val tests = this{
    "generateResources" - {
      val imageName = "fsat/testimpl:1.0.0-SNAPSHOT"

      val kubernetesArgs = KubernetesArgs(
        kubernetesVersion = Some(KubernetesVersion(1, 7)))
      val generateDeploymentArgs = GenerateDeploymentArgs(
        dockerImage = Some(imageName),
        targetRuntimeArgs = Some(kubernetesArgs))

      "given valid docker image" - {
        val dockerConfig = Config(
          Config.Cfg(
            Image = Some(imageName),
            Labels = Some(Map(
              "com.lightbend.rp.app-name" -> "my-app",
              "com.lightbend.rp.version-major" -> "3",
              "com.lightbend.rp.version-minor" -> "2",
              "com.lightbend.rp.version-patch" -> "1",
              "com.lightbend.rp.version-patch-label" -> "SNAPSHOT",
              "com.lightbend.rp.disk-space" -> "65536",
              "com.lightbend.rp.memory" -> "8192",
              "com.lightbend.rp.nr-of-cpus" -> "0.5",
              "com.lightbend.rp.privileged" -> "true",
              "com.lightbend.rp.environment-variables.0.type" -> "literal",
              "com.lightbend.rp.environment-variables.0.name" -> "testing1",
              "com.lightbend.rp.environment-variables.0.value" -> "testingvalue1",
              "com.lightbend.rp.environment-variables.0.some-key" -> "test",
              "com.lightbend.rp.environment-variables.1.type" -> "configMap",
              "com.lightbend.rp.environment-variables.1.name" -> "testing2",
              "com.lightbend.rp.environment-variables.1.map-name" -> "mymap",
              "com.lightbend.rp.environment-variables.1.key" -> "mykey",
              "com.lightbend.rp.environment-variables.2.type" -> "fieldRef",
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
              "com.lightbend.rp.endpoints.0.acls.0.type" -> "http",
              "com.lightbend.rp.endpoints.0.acls.0.expression" -> "^/.*",
              "com.lightbend.rp.endpoints.0.some-key" -> "test",
              "com.lightbend.rp.endpoints.0.acls.0.some-key" -> "test",
              "com.lightbend.rp.endpoints.1.name" -> "ep2",
              "com.lightbend.rp.endpoints.1.protocol" -> "tcp",
              "com.lightbend.rp.endpoints.1.version" -> "1",
              "com.lightbend.rp.endpoints.1.port" -> "1234",
              "com.lightbend.rp.endpoints.2.name" -> "ep3",
              "com.lightbend.rp.endpoints.2.protocol" -> "udp",
              "com.lightbend.rp.endpoints.2.port" -> "1234"))))

        def getDockerConfig(input: String): Try[Config] = {
          assert(input == imageName)
          Success(dockerConfig)
        }

        "generates kubernetes deployment + service resource" - {
          def handleOutput(generatedResources: Seq[GeneratedKubernetesResource]): Unit = {
            val (deployment, service) = generatedResources match {
              case Seq(deployment: Deployment, service: Service) =>
                deployment -> service
            }

            assert(deployment.name == "my-app-v3.2.1-SNAPSHOT")
            val deploymentJsonExpected =
              """
                |{
                |	"apiVersion": "apps/v1beta1",
                |	"kind": "Deployment",
                |	"metadata": {
                |		"name": "my-app-v3.2.1-SNAPSHOT",
                |		"labels": {
                |			"app": "my-app",
                |			"appVersionMajor": "my-app-v3",
                |			"appVersionMajorMinor": "my-app-v3.2",
                |			"appVersion": "my-app-v3.2.1-SNAPSHOT"
                |		}
                |	},
                |	"spec": {
                |		"replicas": 1,
                |		"serviceName": "my-app-v3",
                |		"template": {
                |			"app": "my-app",
                |			"appVersionMajor": "my-app-v3",
                |			"appVersionMajorMinor": "my-app-v3.2",
                |			"appVersion": "my-app-v3.2.1-SNAPSHOT"
                |		},
                |		"spec": {
                |			"containers": [{
                |				"name": "my-app",
                |				"image": "fsat/testimpl:1.0.0-SNAPSHOT",
                |				"imagePullPolicy": "IfNotPresent",
                |				"env": [{
                |					"name": "testing1",
                |					"value": "testingvalue1"
                |				}, {
                |					"name": "testing2",
                |					"valueFrom": {
                |						"configMapKeyRef": {
                |							"name": "mymap",
                |							"key": "mykey"
                |						}
                |					}
                |				}, {
                |					"name": "testing3",
                |					"valueFrom": {
                |						"fieldRef": {
                |							"fieldPath": "metadata.name"
                |						}
                |					}
                |				}],
                |				"ports": [{
                |					"containerPort": 0,
                |					"name": "ep1-v9"
                |				}, {
                |					"containerPort": 1234,
                |					"name": "ep2-v1"
                |				}, {
                |					"containerPort": 1234,
                |					"name": "ep3-v3"
                |				}]
                |			}]
                |		}
                |	}
                |}
              """.stripMargin.parse.right.get
            assert(deployment.payload == deploymentJsonExpected)

            assert(service.name == "my-app")
            val serviceJsonExpected =
              """
                |{
                |	"apiVersion": "v1",
                |	"kind": "Service",
                |	"metadata": {
                |		"labels": {
                |			"app": "my-app"
                |		},
                |		"name": "my-app"
                |	},
                |	"spec": {
                |		"clusterIP": "None",
                |		"ports": [{
                |			"name": "ep1-v9",
                |			"port": 0,
                |			"protocol": "TCP",
                |			"targetPort": 0
                |		}, {
                |			"name": "ep2-v1",
                |			"port": 1234,
                |			"protocol": "TCP",
                |			"targetPort": 1234
                |		}, {
                |			"name": "ep3-v3",
                |			"port": 1234,
                |			"protocol": "UDP",
                |			"targetPort": 1234
                |		}],
                |		"selector": {
                |			"app": "my-app"
                |		}
                |	}
                |}
              """.stripMargin.parse.right.get
            assert(service.payload == serviceJsonExpected)
          }

          val result = generateResources(getDockerConfig, handleOutput)(generateDeploymentArgs, kubernetesArgs)

          assert(result.isSuccess)
        }

        "generates kubernetes nginx ingress resource" - {
          val kubernetesArgsWithNginxIngress = kubernetesArgs.copy(
            ingressArgs = Some(IngressArgs.IngressNgnixArgs(
              tlsSecretName = Some("secret"),
              sslRedirect = true)))

          val generateDeploymentArgsWithIngress = generateDeploymentArgs.copy(
            targetRuntimeArgs = Some(kubernetesArgsWithNginxIngress))

          def handleOutput(generatedResources: Seq[GeneratedKubernetesResource]): Unit = {
            val (_, _, ingress) = generatedResources match {
              case Seq(deployment: Deployment, service: Service, ingress: IngressNginx) =>
                (deployment, service, ingress)
            }

            assert(ingress.name == "my-app")
            val ingressJsonExpected =
              """
                |{
                |	"apiVersion": "extensions/v1beta1",
                |	"kind": "Ingress",
                |	"metadata": {
                |		"name": "my-app",
                |		"annotations": {
                |			"ingress.kubernetes.io/ssl-redirect": "true"
                |		}
                |	},
                |	"spec": {
                |		"rules": [{
                |			"http": {
                |				"paths": [{
                |					"path": "^/.*",
                |					"backend": {
                |						"serviceName": "ep1-v9",
                |						"servicePort": 0
                |					}
                |				}]
                |			}
                |		}],
                |		"tls": [{
                |			"secretName": "secret"
                |		}]
                |	}
                |}
              """.stripMargin.parse.right.get
            assert(ingress.payload == ingressJsonExpected)
          }

          val result = generateResources(getDockerConfig, handleOutput)(generateDeploymentArgsWithIngress, kubernetesArgsWithNginxIngress)

          assert(result.isSuccess)
        }

        "generates kubernetes istio ingress resource" - {
          val kubernetesArgsWithIstioIngress = kubernetesArgs.copy(
            ingressArgs = Some(IngressArgs.IngressIstioArgs))

          val generateDeploymentArgsWithIngress = generateDeploymentArgs.copy(
            targetRuntimeArgs = Some(kubernetesArgsWithIstioIngress))

          def handleOutput(generatedResources: Seq[GeneratedKubernetesResource]): Unit = {
            val (_, _, ingress) = generatedResources match {
              case Seq(deployment: Deployment, service: Service, ingress: IngressIstio) =>
                (deployment, service, ingress)
            }

            assert(ingress.name == "my-app")
            val ingressJsonExpected =
              """
                |{
                |	"apiVersion": "extensions/v1beta1",
                |	"kind": "Ingress",
                |	"metadata": {
                |		"name": "my-app",
                |		"annotations": {
                |			"kubernetes.io/ingress.class": "istio"
                |		}
                |	},
                |	"spec": {
                |		"rules": [{
                |			"http": {
                |				"paths": [{
                |					"path": "^/.*.*",
                |					"backend": {
                |						"serviceName": "ep1-v9",
                |						"servicePort": 0
                |					}
                |				}]
                |			}
                |		}]
                |	}
                |}
              """.stripMargin.parse.right.get

            assert(ingress.payload == ingressJsonExpected)
          }

          val result = generateResources(getDockerConfig, handleOutput)(generateDeploymentArgsWithIngress, kubernetesArgsWithIstioIngress)

          assert(result.isSuccess)
        }
      }

      "handles failure getting docker image" - {
        val error = new RuntimeException("test only")
        def getDockerConfigFailed(input: String): Try[Config] = {
          assert(input == imageName)
          Failure(error)
        }

        def handleOutput(generatedResources: Seq[GeneratedKubernetesResource]): Unit = {
          throw new IllegalArgumentException("This should not be called")
        }

        val result = generateResources(getDockerConfigFailed, handleOutput)(generateDeploymentArgs, kubernetesArgs)

        assert(result.isFailure)
        result.recover {
          case e => assert(e == error)
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

        handleGeneratedResources(KubernetesArgs.Output.PipeToKubeCtl(printStream))(generatedResources)

        printStream.close()

        val generatedText = new String(output.toByteArray)
        val expectedText =
          """---
            |{"key1":"value1"}
            |---
            |{"key2":"value2"}
            |""".stripMargin
        assert(generatedText == expectedText)
      }
    }
  }
}
