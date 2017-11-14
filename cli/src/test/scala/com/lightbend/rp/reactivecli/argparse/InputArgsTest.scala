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

package com.lightbend.rp.reactivecli.argparse

import java.nio.file.{ Files, Paths }

import com.lightbend.rp.reactivecli.argparse.kubernetes.{ DeploymentArgs, IngressArgs, KubernetesArgs, ServiceArgs }
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment.KubernetesVersion
import slogging.LogLevel
import utest._

object InputArgsTest extends TestSuite {
  val parser = InputArgs.parser("reactive-cli", "0.1.0")

  def mkArgs(input: String): Seq[String] =
    input.split("\n").flatMap(_.split(" ")).map(_.trim).filterNot(_.isEmpty)

  val tests = this{
    "argument parsing" - {
      "generate deployment" - {
        "kubernetes" - {
          "minimum arguments" - {
            val result = parser.parse(
              Seq(
                "generate-deployment",
                "dockercloud/hello-world:1.0.0-SNAPSHOT",
                "--target", "kubernetes",
                "--kubernetes-version", "1.7"),
              InputArgs.default)
            assert(
              result.contains(
                InputArgs(
                  commandArgs = Some(GenerateDeploymentArgs(
                    dockerImage = Some("dockercloud/hello-world:1.0.0-SNAPSHOT"),
                    targetRuntimeArgs = Some(KubernetesArgs(
                      kubernetesVersion = Some(KubernetesVersion(1, 7)))))))))
          }

          "all arguments" - {
            val mockCacerts = Files.createTempFile("cacerts", "test")

            try {
              val result = parser.parse(
                Seq(
                  "generate-deployment",
                  "--loglevel", "debug",
                  "--cainfo", mockCacerts.toAbsolutePath.toString,
                  "dockercloud/hello-world:1.0.0-SNAPSHOT",
                  "--target", "kubernetes",
                  "--kubernetes-version", "1.7",
                  "--kubernetes-deployment-nr-of-replicas", "10",
                  "--kubernetes-deployment-image-pull-policy", "Always",
                  "--kubernetes-service-cluster-ip", "10.0.0.1",
                  "--kubernetes-ingress-annotation", "ing=123",
                  "--kubernetes-ingress-path-append", ".*",
                  "--env", "test1=test2",
                  "--nr-of-cpus", "0.5",
                  "--memory", "1024",
                  "--disk-space", "2048",
                  "--output", "/tmp/foo",
                  "--registry-username", "john",
                  "--registry-password", "wick",
                  "--registry-https-disable",
                  "--registry-tls-validation-disable"),
                InputArgs.default)
              assert(
                result.contains(
                  InputArgs(
                    logLevel = LogLevel.DEBUG,
                    tlsCacertsPath = Some(mockCacerts),
                    commandArgs = Some(GenerateDeploymentArgs(
                      dockerImage = Some("dockercloud/hello-world:1.0.0-SNAPSHOT"),
                      targetRuntimeArgs = Some(KubernetesArgs(
                        kubernetesVersion = Some(KubernetesVersion(1, 7)),
                        output = KubernetesArgs.Output.SaveToFile(Paths.get("/tmp/foo")),
                        deploymentArgs = DeploymentArgs(
                          numberOfReplicas = 10,
                          imagePullPolicy = Deployment.ImagePullPolicy.Always),
                        serviceArgs = ServiceArgs(clusterIp = Some("10.0.0.1")),
                        ingressArgs = IngressArgs(
                          ingressAnnotations = Map("ing" -> "123"),
                          pathAppend = Some(".*")))),
                      environmentVariables = Map("test1" -> "test2"),
                      nrOfCpus = Some(0.5),
                      memory = Some(1024),
                      diskSpace = Some(2048),
                      registryUsername = Some("john"),
                      registryPassword = Some("wick"),
                      registryUseHttps = false,
                      registryValidateTls = false)))))
            } finally {
              Files.deleteIfExists(mockCacerts)
            }

            "registry credentials" - {
              val baseArgs = Seq(
                "generate-deployment",
                "dockercloud/hello-world:1.0.0-SNAPSHOT",
                "--target", "kubernetes",
                "--kubernetes-version", "1.7")

              "should fail if username is defined but password is empty" - {
                val result = parser.parse(
                  baseArgs ++ Seq(
                    "--registry-username", "john"),
                  InputArgs.default)

                assert(result.isEmpty)
              }

              "should fail if username is empty but password is defined" - {
                val result = parser.parse(
                  baseArgs ++ Seq(
                    "--registry-password", "wick"),
                  InputArgs.default)

                assert(result.isEmpty)
              }
            }
          }
        }
      }

      "with default arguments" - {
        val result = parser.parse(Seq.empty, InputArgs.default)
        assert(result.contains(InputArgs.default))
      }
    }

    "merge with envs" - {
      "RP_CAINFO" - {
        "should use the value from environment" - {
          val inputArgs = InputArgs()
          val result = InputArgs.Envs.mergeWithEnvs(inputArgs, Map(InputArgs.Envs.RP_CAINFO -> "/path/cacerts"))
          val expectedResult = InputArgs(
            tlsCacertsPath = Some(Paths.get("/path/cacerts")))
          assert(result == expectedResult)
        }

        "should prefer value from the input args" - {
          val inputArgs = InputArgs(
            tlsCacertsPath = Some(Paths.get("/my/cacerts")))
          val result = InputArgs.Envs.mergeWithEnvs(inputArgs, Map(InputArgs.Envs.RP_CAINFO -> "/path/cacerts"))
          val expectedResult = InputArgs(
            tlsCacertsPath = Some(Paths.get("/my/cacerts")))
          assert(result == expectedResult)
        }
      }
    }
  }
}
