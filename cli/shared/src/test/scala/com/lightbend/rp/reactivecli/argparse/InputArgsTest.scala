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

import com.lightbend.rp.reactivecli.argparse.kubernetes.{ IngressArgs, KubernetesArgs, PodControllerArgs, ServiceArgs }
import com.lightbend.rp.reactivecli.argparse.marathon.MarathonArgs
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.json.JsonTransformExpression
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment

import scala.collection.immutable.Seq
import scala.concurrent.Future
import slogging.LogLevel
import utest._

object InputArgsTest extends TestSuite {
  val parser = InputArgs.parser("reactive-cli", "0.1.0")

  def mkArgs(input: String): Array[String] =
    input.split("\n").flatMap(_.split(" ")).map(_.trim).filterNot(_.isEmpty)

  val tests = this{
    "argument parsing" - {
      "generate deployment" - {
        "kubernetes" - {
          "fail without generate flags" - {
            val result = parser.parse(
              Seq(
                "generate-kubernetes-resources",
                "dockercloud/hello-world:1.0.0-SNAPSHOT"),
              InputArgs.default)
            assert(result.isEmpty)
          }

          "minimum arguments" - {
            val result = parser.parse(
              Seq(
                "generate-kubernetes-resources",
                "dockercloud/hello-world:1.0.0-SNAPSHOT",
                "--generate-all"),
              InputArgs.default)
            assert(
              result.contains(
                InputArgs(
                  commandArgs = Some(GenerateDeploymentArgs(
                    dockerImages = Seq("dockercloud/hello-world:1.0.0-SNAPSHOT"),
                    targetRuntimeArgs = Some(
                      KubernetesArgs(
                        generateIngress = true,
                        generatePodControllers = true,
                        generateServices = true)))))))
          }

          "all arguments" - {
            withTempFile { mockCacerts =>
              val res = parser
                .parse(
                  Seq(
                    "generate-kubernetes-resources",
                    "--loglevel", "debug",
                    "--cainfo", mockCacerts,
                    "dockercloud/hello-world:1.0.0-SNAPSHOT",
                    "--namespace", "chirper",
                    "--apps-api-version", "hello1",
                    "--batch-api-version", "hello2",
                    "--pod-controller-replicas", "10",
                    "--pod-controller-image-pull-policy", "Always",
                    "--service-cluster-ip", "10.0.0.1",
                    "--ingress-annotation", "ing=123",
                    "--ingress-path-suffix", ".*",
                    "--ingress-api-version", "hello2",
                    "--ingress-tls-secret", "hello1",
                    "--ingress-tls-secret", "hello2",
                    "--env", "test1=test2",
                    "--output", "/tmp/foo",
                    "--registry-username", "john",
                    "--registry-password", "wick",
                    "--registry-disable-https",
                    "--registry-disable-tls-validation",
                    "--service-api-version", "hello3",
                    "--external-service", "cas1=1.2.3.4",
                    "--external-service", "cas1=5.6.7.8",
                    "--external-service", "cas2=hello",
                    "--generate-all",
                    "--generate-ingress",
                    "--generate-namespaces",
                    "--generate-pod-controllers",
                    "--generate-services",
                    "--deployment-type", "rolling",
                    "--akka-cluster-join-existing",
                    "--akka-cluster-skip-validation",
                    "--name", "test",
                    "--pod-controller-type", "job",
                    "--service-type", "NodePort",
                    "--service-load-balancer-ip", "10.0.0.2"),
                  InputArgs.default)
                .get

              Future.successful {
                // We can't compare futures, so we have to check each parameter

                assert(res.logLevel == LogLevel.DEBUG)
                assert(res.tlsCacertsPath == Some(mockCacerts))

                val commandArgs = res.commandArgs.get.asInstanceOf[GenerateDeploymentArgs]

                val targetRuntimeArgs = commandArgs.targetRuntimeArgs.get.asInstanceOf[KubernetesArgs]

                assert(commandArgs.name == Some("test"))
                assert(commandArgs.deploymentType == RollingDeploymentType)
                assert(commandArgs.dockerImages == Seq("dockercloud/hello-world:1.0.0-SNAPSHOT"))
                assert(commandArgs.environmentVariables == Map("test1" -> "test2"))
                assert(commandArgs.registryUsername == Some("john"))
                assert(commandArgs.registryPassword == Some("wick"))
                assert(!commandArgs.registryUseHttps)
                assert(!commandArgs.registryValidateTls)
                assert(commandArgs.externalServices == Map("cas1" -> Seq("1.2.3.4", "5.6.7.8"), "cas2" -> Seq("hello")))
                assert(commandArgs.akkaClusterJoinExisting)
                assert(commandArgs.akkaClusterSkipValidation)

                assert(targetRuntimeArgs.generateIngress)
                assert(targetRuntimeArgs.generateNamespaces)
                assert(targetRuntimeArgs.generatePodControllers)
                assert(targetRuntimeArgs.generateServices)
                assert(targetRuntimeArgs.namespace == Some("chirper"))
                assert(targetRuntimeArgs.output == KubernetesArgs.Output.SaveToFile("/tmp/foo"))
                assert(targetRuntimeArgs.podControllerArgs.appsApiVersion.value.get.get == "hello1")
                assert(targetRuntimeArgs.podControllerArgs.batchApiVersion.value.get.get == "hello2")
                assert(targetRuntimeArgs.podControllerArgs.numberOfReplicas == 10)
                assert(targetRuntimeArgs.serviceArgs.apiVersion.value.get.get == "hello3")
                assert(targetRuntimeArgs.serviceArgs.clusterIp == Some("10.0.0.1"))
                assert(targetRuntimeArgs.serviceArgs.loadBalancerIp == Some("10.0.0.2"))
                assert(targetRuntimeArgs.serviceArgs.serviceType == Some("NodePort"))
                assert(targetRuntimeArgs.ingressArgs.apiVersion.value.get.get == "hello2")
                assert(targetRuntimeArgs.ingressArgs.ingressAnnotations == Map("ing" -> "123"))
                assert(targetRuntimeArgs.ingressArgs.pathAppend == Some(".*"))
                assert(targetRuntimeArgs.ingressArgs.tlsSecrets == Seq("hello1", "hello2"))
                assert(targetRuntimeArgs.podControllerArgs.controllerType == PodControllerArgs.ControllerType.Job)
              }
            }
          }

          "registry credentials" - {
            val baseArgs = Seq(
              "generate-kubernetes-resources",
              "dockercloud/hello-world:1.0.0-SNAPSHOT")

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

        "marathon" - {
          withTempFile { mockCacerts =>
            val expected =
              InputArgs(
                logLevel = LogLevel.DEBUG,
                tlsCacertsPath = Some(mockCacerts),
                commandArgs = Some(GenerateDeploymentArgs(
                  application = Some("test"),
                  akkaClusterJoinExisting = true,
                  akkaClusterSkipValidation = true,
                  deploymentType = CanaryDeploymentType,
                  dockerImages = Seq("dockercloud/hello-world:1.0.0-SNAPSHOT"),
                  name = Some("test"),
                  version = Some("1.0.0"),
                  environmentVariables = Map("test1" -> "test2"),
                  cpu = None,
                  memory = None,
                  diskSpace = None,
                  targetRuntimeArgs = Some(
                    MarathonArgs(
                      instances = 10,
                      marathonLbHaproxyGroup = "external2",
                      marathonLbHaproxyHosts = Seq("test.com"),
                      namespace = Some("chirper"),
                      output = MarathonArgs.Output.SaveToFile("/tmp/foo"),
                      registryForcePull = true,
                      transformOutput = Some(JsonTransformExpression(".test = 123")))),
                  registryUsername = Some("john"),
                  registryPassword = Some("wick"),
                  registryUseHttps = false,
                  registryValidateTls = false,
                  externalServices = Map("cas1" -> Seq("1.2.3.4", "5.6.7.8"), "cas2" -> Seq("hello")))))

            val actual = parser
              .parse(
                Seq(
                  "generate-marathon-configuration",
                  "--loglevel", "debug",
                  "--application", "test",
                  "--cainfo", mockCacerts,
                  "dockercloud/hello-world:1.0.0-SNAPSHOT",
                  "--namespace", "chirper",
                  "--instances", "10",
                  "--registry-force-pull", "true",
                  "--env", "test1=test2",
                  "--output", "/tmp/foo",
                  "--registry-username", "john",
                  "--registry-password", "wick",
                  "--registry-disable-https",
                  "--registry-disable-tls-validation",
                  "--external-service", "cas1=1.2.3.4",
                  "--external-service", "cas1=5.6.7.8",
                  "--external-service", "cas2=hello",
                  "--transform-output", ".test = 123",
                  "--deployment-type", "canary",
                  "--akka-cluster-join-existing",
                  "--akka-cluster-skip-validation",
                  "--name", "test",
                  "--version", "1.0.0",
                  "--marathon-lb-group", "external2",
                  "--marathon-lb-host", "test.com"),
                InputArgs.default)
              .get

            Future.successful {
              assert(expected == actual)
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
            tlsCacertsPath = Some("/path/cacerts"))
          assert(result == expectedResult)
        }

        "should prefer value from the input args" - {
          val inputArgs = InputArgs(
            tlsCacertsPath = Some("/my/cacerts"))
          val result = InputArgs.Envs.mergeWithEnvs(inputArgs, Map(InputArgs.Envs.RP_CAINFO -> "/path/cacerts"))
          val expectedResult = InputArgs(
            tlsCacertsPath = Some("/my/cacerts"))
          assert(result == expectedResult)
        }
      }
    }
  }
}
