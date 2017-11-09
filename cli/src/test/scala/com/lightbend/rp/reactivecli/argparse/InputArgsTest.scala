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

import java.nio.file.Paths

import com.lightbend.rp.reactivecli.argparse.kubernetes.IngressArgs.{ IngressIstioArgs, IngressNgnixArgs }
import com.lightbend.rp.reactivecli.argparse.kubernetes.{ DeploymentArgs, KubernetesArgs, ServiceArgs }
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

          "all arguments + nginx ingress" - {
            val result = parser.parse(
              Seq(
                "generate-deployment",
                "dockercloud/hello-world:1.0.0-SNAPSHOT",
                "--target", "kubernetes",
                "--kubernetes-version", "1.7",
                "--kubernetes-deployment-nr-of-replicas", "10",
                "--kubernetes-deployment-image-pull-policy", "Always",
                "--kubernetes-service-cluster-ip", "10.0.0.1",
                "--kubernetes-ingress", "nginx",
                "--kubernetes-ingress-nginx-tls-secret-name", "secret",
                "--kubernetes-ingress-nginx-ssl-redirect", "true",
                "--env", "test1=test2",
                "--nr-of-cpus", "0.5",
                "--memory", "1024",
                "--disk-space", "2048",
                "--output", "/tmp/foo",
                "--loglevel", "debug"),
              InputArgs.default)
            assert(
              result.contains(
                InputArgs(
                  logLevel = LogLevel.DEBUG,
                  commandArgs = Some(GenerateDeploymentArgs(
                    dockerImage = Some("dockercloud/hello-world:1.0.0-SNAPSHOT"),
                    targetRuntimeArgs = Some(KubernetesArgs(
                      kubernetesVersion = Some(KubernetesVersion(1, 7)),
                      output = KubernetesArgs.Output.SaveToFile(Paths.get("/tmp/foo")),
                      deploymentArgs = DeploymentArgs(
                        numberOfReplicas = 10,
                        imagePullPolicy = Deployment.ImagePullPolicy.Always),
                      serviceArgs = ServiceArgs(clusterIp = Some("10.0.0.1")),
                      ingressArgs = Some(IngressNgnixArgs(
                        tlsSecretName = Some("secret"),
                        sslRedirect = true)))),
                    environmentVariables = Map("test1" -> "test2"),
                    nrOfCpus = Some(0.5),
                    memory = Some(1024),
                    diskSpace = Some(2048))))))
          }

          "default argument + istio ingress" - {
            "minimum arguments" - {
              val result = parser.parse(
                Seq(
                  "generate-deployment",
                  "dockercloud/hello-world:1.0.0-SNAPSHOT",
                  "--target", "kubernetes",
                  "--kubernetes-version", "1.7",
                  "--kubernetes-ingress", "istio"),
                InputArgs.default)
              assert(
                result.contains(
                  InputArgs(
                    commandArgs = Some(GenerateDeploymentArgs(
                      dockerImage = Some("dockercloud/hello-world:1.0.0-SNAPSHOT"),
                      targetRuntimeArgs = Some(KubernetesArgs(
                        kubernetesVersion = Some(KubernetesVersion(1, 7)),
                        ingressArgs = Some(IngressIstioArgs))))))))
            }
          }
        }
      }

      "with default arguments" - {
        val result = parser.parse(Seq.empty, InputArgs.default)
        assert(result.contains(InputArgs.default))
      }
    }
  }
}
