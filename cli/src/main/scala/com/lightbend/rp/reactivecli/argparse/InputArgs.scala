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

import java.io.File

import com.lightbend.rp.reactivecli.argparse.kubernetes.{ DeploymentArgs, IngressArgs, KubernetesArgs, ServiceArgs }
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment.{ ImagePullPolicy, KubernetesVersion }
import scopt.OptionParser
import slogging.LogLevel

object InputArgs {
  implicit val logLevelsRead: scopt.Read[LogLevel] =
    scopt.Read.reads {
      case v if v.toLowerCase == "error" => LogLevel.ERROR
      case v if v.toLowerCase == "warn" => LogLevel.WARN
      case v if v.toLowerCase == "info" => LogLevel.INFO
      case v if v.toLowerCase == "debug" => LogLevel.DEBUG
      case v if v.toLowerCase == "trace" => LogLevel.TRACE
      case v =>
        throw new IllegalArgumentException(s"Invalid log level $v. Available: error, warn, info, debug, trace")
    }

  implicit val imagePullPolicyRead: scopt.Read[ImagePullPolicy.Value] =
    scopt.Read.reads(ImagePullPolicy.withName)

  implicit val kubernetesVersionRead: scopt.Read[KubernetesVersion] =
    scopt.Read.reads { v =>
      v.split("\\.").toSeq match {
        case Seq(major, minor) =>
          KubernetesVersion(major.toInt, minor.toInt)
        case _ =>
          throw new IllegalArgumentException(s"Invalid Kubernetes version number $v. Example: 1.6")
      }
    }

  val default = InputArgs()

  /**
   * Builds the Scopt parser which is able to parse user input arguments into the CLI.
   */
  def parser(cliName: String, cliVersion: String): OptionParser[InputArgs] =
    new OptionParser[InputArgs](cliName) {
      head(cliName, cliVersion)

      help("help")
        .text("Print this help text")

      opt[LogLevel]('l', "loglevel")
        .text("Sets the log level. Available: error, warn, info, debug, trace")
        .action((v, c) => c.copy(logLevel = v))

      cmd("generate-deployment")
        .text("Generate deployment resources")
        .action((_, inputArgs) => inputArgs.copy(commandArgs = Some(GenerateDeploymentArgs())))
        .children(
          arg[String]("docker-image")
            .text("Docker image to be deployed. Format: [<registry host>/][<repo>/]image[:tag]")
            .required()
            .action(GenerateDeploymentArgs.set((v, args) => args.copy(dockerImage = Some(v)))),

          opt[String]("target")
            .text("Generates the resource for target runtime. Supported: kubernetes")
            .required()
            .validate {
              case v if v.toLowerCase == "kubernetes" => success
              case v => failure(s"Unsupported target runtime: $v")
            }
            .action(GenerateDeploymentArgs.set {
              case (v, args) if v.toLowerCase == "kubernetes" => args.copy(targetRuntimeArgs = Some(KubernetesArgs()))
              case (_, args) => args
            })
            .children(
              opt[File]('o', "output")
                .text("Specify the output directory of the generated resources")
                .optional()
                .action(KubernetesArgs.set((v, args) => args.copy(output = KubernetesArgs.Output.SaveToFile(v.toPath)))),

              opt[KubernetesVersion]("kubernetes-version")
                .text("Kubernetes major and minor version. Example: 1.6")
                .required()
                .action(KubernetesArgs.set((v, args) => args.copy(kubernetesVersion = Some(v)))),

              opt[Int]("kubernetes-deployment-nr-of-replicas")
                .text("Sets the number of replicas set for Kubernetes deployment resource")
                .validate(v => if (v >= 0) success else failure("Number of replicas must be zero or more"))
                .action(DeploymentArgs.set((v, args) => args.copy(numberOfReplicas = v))),

              opt[ImagePullPolicy.Value]("kubernetes-deployment-image-pull-policy")
                .text(s"Sets the docker image pull policy for Kubernetes deployment resource. Option: ${Deployment.ImagePullPolicy.values.mkString(", ")}")
                .action(DeploymentArgs.set((v, args) => args.copy(imagePullPolicy = v))),

              opt[String]("kubernetes-service-cluster-ip")
                .text("Sets the cluster IP for Kubernetes service resource")
                .action(ServiceArgs.set((v, args) => args.copy(clusterIp = Some(v)))),

              opt[String]("kubernetes-ingress")
                .optional()
                .text("Specifies the Kubernetes ingress options. Supported: istio, nginx")
                .validate {
                  case v if v.toLowerCase == "nginx" || v.toLowerCase == "istio" => success
                  case v => failure(s"Unsupported ingress option: $v")
                }
                .action(KubernetesArgs.set {
                  case (v, args) if v.toLowerCase == "nginx" => args.copy(ingressArgs = Some(IngressArgs.IngressNgnixArgs()))
                  case (v, args) if v.toLowerCase == "istio" => args.copy(ingressArgs = Some(IngressArgs.IngressIstioArgs))
                  case (_, args) => args
                })
                .children(
                  opt[String]("kubernetes-ingress-nginx-tls-secret-name")
                    .text("Specifies the TLS secret name for Kubernetes nginx ingress resource")
                    .action(IngressArgs.IngressNgnixArgs.set((v, args) => args.copy(tlsSecretName = Some(v)))),

                  opt[Boolean]("kubernetes-ingress-nginx-ssl-redirect")
                    .text("Enables/disables the SSL redirect for Kubernetes nginx ingress resource")
                    .action(IngressArgs.IngressNgnixArgs.set((v, args) => args.copy(sslRedirect = v))))),

          opt[String]("env")
            .text("Sets an environment variable. Format: NAME=value")
            .minOccurs(0)
            .unbounded()
            .action(GenerateDeploymentArgs.set {
              (v, c) =>
                val parts = v.split("=", 2).lift
                c.copy(
                  environmentVariables = c.environmentVariables.updated(
                    parts(0).get,
                    parts(1).getOrElse("")))
            }),

          opt[Double]("nr-of-cpus")
            .text("Specify the number of CPU shares")
            .optional()
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(nrOfCpus = Some(v)))),

          opt[Long]("memory")
            .text("Specify the memory limit")
            .optional()
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(memory = Some(v)))),

          opt[Long]("disk-space")
            .text("Specify the disk space limit")
            .optional()
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(diskSpace = Some(v)))))
    }

}

/**
 * Represents the user input into the CLI.
 */
case class InputArgs(
  logLevel: LogLevel = LogLevel.INFO,
  commandArgs: Option[CommandArgs] = None)
