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
import java.nio.file.{ Path, Paths }

import com.lightbend.rp.reactivecli.annotations.LiteralEnvironmentVariable
import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs.RpJavaOptsMergeStrategy
import com.lightbend.rp.reactivecli.argparse.kubernetes.{ DeploymentArgs, IngressArgs, KubernetesArgs, ServiceArgs }
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment.{ ImagePullPolicy, KubernetesVersion }

import scala.collection.immutable.Seq
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
      v.split("\\.").toVector match {
        case Vector(major, minor) =>
          KubernetesVersion(major.toInt, minor.toInt)
        case _ =>
          throw new IllegalArgumentException(s"Invalid Kubernetes version number $v. Example: 1.6")
      }
    }

  implicit val rpJavaOptsMergeStrategyRead: scopt.Read[RpJavaOptsMergeStrategy.Value] =
    scopt.Read.reads(RpJavaOptsMergeStrategy.withName)

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

      cmd("version")
        .text("Outputs the program's version")
        .action((_, inputArgs) => inputArgs.copy(commandArgs = Some(VersionArgs)))

      opt[File]("cainfo")
        .text("Path to the CA certs for TLS validation purposes")
        .validate(f => if (f.exists()) success else failure(s"CA certs file ${f.getAbsolutePath} doesn't exist."))
        .action((f, c) => c.copy(tlsCacertsPath = Some(f.toPath.toAbsolutePath)))

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

              opt[String]("kubernetes-namespace")
                .text("Kubernetes namespace of the artefact to be generated")
                .action(KubernetesArgs.set((v, args) => args.copy(kubernetesNamespace = Some(v)))),

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

              opt[String]("kubernetes-ingress-annotation")
                .text("Specifies the Kubernetes ingress annotation. Format: NAME=value")
                .minOccurs(0)
                .unbounded()
                .action(IngressArgs.set {
                  (v, c) =>
                    val parts = v.split("=", 2).lift
                    c.copy(
                      ingressAnnotations = c.ingressAnnotations.updated(
                        parts(0).get,
                        parts(1).getOrElse("")))
                }),

              opt[String]("kubernetes-ingress-path-append")
                .text("Appends the expression specified to the ingress path. For example: if .* is specified, then the ingress path /my-path will be rendered as /my-path.*")
                .action(IngressArgs.set((v, c) => c.copy(pathAppend = Some(v))))),

          opt[String]("env")
            .text("Sets an environment variable. Format: NAME=value")
            .minOccurs(0)
            .unbounded()
            .validate { v =>
              val parts = v.split("=", 2).lift
              (parts(0), parts(1)) match {
                case (Some(Envs.RP_JAVA_OPTS), Some(_)) =>
                  failure(s"Please specify ${Envs.RP_JAVA_OPTS} using the --rp-java-opts option")
                case (Some(k), Some(_)) =>
                  success
                case _ =>
                  failure("Invalid environment variable format. Format: NAME=value")
              }
            }
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
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(diskSpace = Some(v)))),

          opt[String]("rp-java-opts")
            .text(s"Specify the java options to be passed into the application as ${Envs.RP_JAVA_OPTS} environment variable. Format: -Dmy.option=my.value")
            .minOccurs(0)
            .unbounded()
            .validate { v =>
              val parts = v.split("=", 2).lift
              (parts(0), parts(1)) match {
                case (Some(k), Some(_)) if k.startsWith("-D") =>
                  success
                case _ =>
                  failure("Invalid RP_JAVA_OPTS format. Format: -Dmy.option=my.value")
              }
            }
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(rpJavaOpts = c.rpJavaOpts :+ LiteralEnvironmentVariable(v)))),

          opt[RpJavaOptsMergeStrategy.Value]("rp-java-opts-merge")
            .text(s"Specifies how the RP_JAVA_OPTS specified from the input argument is merged with the labels: ${RpJavaOptsMergeStrategy.values.mkString(", ")}. Default: ${RpJavaOptsMergeStrategy.DefaultMergeStrategy}")
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(rpJavaOptsMergeStrategy = v))),

          opt[String]("registry-username")
            .text("Specify username to access docker registry. Password must be specified also.")
            .optional()
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(registryUsername = Some(v)))),

          opt[String]("registry-password")
            .text("Specify password to access docker registry. Username must be specified also.")
            .optional()
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(registryPassword = Some(v)))),

          opt[Unit]("registry-https-disable")
            .text("Disables HTTPS when accessing docker registry")
            .optional()
            .action(GenerateDeploymentArgs.set((_, c) => c.copy(registryUseHttps = false))),

          opt[Unit]("registry-tls-validation-disable")
            .text("Disables TLS cert validation when accessing docker registry through HTTPS")
            .optional()
            .action(GenerateDeploymentArgs.set((_, c) => c.copy(registryValidateTls = false))))

      opt[String]("external-service")
        .text("Declare an external service address. Format: NAME=value")
        .optional()
        .minOccurs(0)
        .unbounded()
        .action(GenerateDeploymentArgs.set {
          (v, c) =>
            val parts = v.split("=", 2)

            if (parts.length == 2) {
              val current = c.externalServices.getOrElse(parts(0), Seq.empty)

              c.copy(externalServices = c.externalServices.updated(parts(0), current :+ parts(1)))
            } else {
              c
            }
        })

      checkConfig { inputArgs =>
        inputArgs.commandArgs match {
          case Some(v: GenerateDeploymentArgs) =>
            if (v.registryUsername.isDefined && v.registryPassword.isEmpty)
              failure("Registry password can't be empty if registry username is specified")
            else if (v.registryUsername.isEmpty && v.registryPassword.isDefined)
              failure("Registry username can't be empty if registry password is specified")
            else
              success
          case _ =>
            success
        }
      }
    }

  object Envs {
    val RP_CAINFO = "RP_CAINFO"
    val RP_JAVA_OPTS = "RP_JAVA_OPTS"

    def mergeWithEnvs(inputArgs: InputArgs, envs: Map[String, String]): InputArgs = {
      val tlsCacertsPathEnv = envs.get(RP_CAINFO).map(Paths.get(_))
      inputArgs.tlsCacertsPath.orElse(tlsCacertsPathEnv)
        .fold(inputArgs)(v => inputArgs.copy(tlsCacertsPath = Some(v)))
    }
  }

}

/**
 * Represents the user input into the CLI.
 */
case class InputArgs(
  logLevel: LogLevel = LogLevel.INFO,
  tlsCacertsPath: Option[Path] = None,
  commandArgs: Option[CommandArgs] = None)
