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

import com.lightbend.rp.reactivecli.argparse.kubernetes.{ PodControllerArgs, IngressArgs, KubernetesArgs, ServiceArgs }
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment.ImagePullPolicy
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scopt.OptionParser
import slogging.LogLevel

object InputArgs {
  implicit val deploymentTypeRead: scopt.Read[DeploymentType] =
    scopt.Read.reads {
      case v if v.toLowerCase == DeploymentType.Canary => CanaryDeploymentType
      case v if v.toLowerCase == DeploymentType.Rolling => RollingDeploymentType
      case v if v.toLowerCase == DeploymentType.BlueGreen => BlueGreenDeploymentType
      case v =>
        throw new IllegalArgumentException(s"Invalid deployment type $v. Available: ${DeploymentType.All.mkString(", ")}")
    }

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

  val default = InputArgs()

  /**
   * Builds the Scopt parser which is able to parse user input arguments into the CLI.
   */
  def parser(cliName: String, cliVersion: String): OptionParser[InputArgs] =
    new OptionParser[InputArgs](cliName) {
      head(cliName, cliVersion)

      opt[String]("cainfo")
        .text("Path to the CA certs for TLS validation purposes")
        .validate(f => if (fileExists(f)) success else failure(s"CA certs file $f doesn't exist."))
        .action((f, c) => c.copy(tlsCacertsPath = Some(f)))

      opt[LogLevel]('l', "loglevel")
        .text("Sets the log level. Available: error, warn, info, debug, trace")
        .action((v, c) => c.copy(logLevel = v))

      help("help")
        .text("Print this help text")

      cmd("version")
        .text("Outputs the program's version")
        .action((_, inputArgs) => inputArgs.copy(commandArgs = Some(VersionArgs)))

      cmd("generate-kubernetes-resources")
        .text("Generate Kubernetes resource files for kubectl")
        .action((_, inputArgs) => inputArgs.copy(commandArgs = Some(GenerateDeploymentArgs(targetRuntimeArgs = Some(KubernetesArgs())))))
        .children(
          arg[String]("docker-image") /* note: this argument will apply for other targets */
            .text("Docker image to be deployed. Format: [<registry host>/][<repo>/]image[:tag]")
            .required()
            .action(GenerateDeploymentArgs.set((v, args) => args.copy(dockerImage = Some(v)))),

          opt[DeploymentType]("deployment-type")
            .text(s"Sets the deployment type. Default: ${DeploymentType.Canary}; Available: ${DeploymentType.All.mkString(", ")}")
            .optional()
            .action(GenerateDeploymentArgs.set((t, args) => args.copy(deploymentType = t))),

          opt[String]("env") /* note: this argument will apply for other targets */
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

          opt[String]("external-service") /* note: this argument will apply for other targets */
            .text("Declares an external service address. Format: NAME=value")
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
            }),

          opt[Unit]("generate-all")
            .text("Generate all resource types. This is the default and overrides other generate flags")
            .action(
              KubernetesArgs.set((v, args) =>
                args.copy(
                  generateIngress = false,
                  generatePodControllers = false,
                  generateServices = false))),

          opt[Unit]("generate-ingress")
            .text("Generate Ingress resources. When provided, Ingress and Pod Controller resource types are not generated unless explicitly requested")
            .action(KubernetesArgs.set((_, args) => args.copy(generateIngress = true))),

          opt[Unit]("generate-namespaces")
            .text("Generate Namespace resources. Does not affect the generation of other resources")
            .action(KubernetesArgs.set((_, args) => args.copy(generateNamespaces = true))),

          opt[Unit]("generate-pod-controllers")
            .text("Generate PodController resources. When provided, Ingress and Pod Controller resource types are not generated unless explicitly requested")
            .action(KubernetesArgs.set((_, args) => args.copy(generatePodControllers = true))),

          opt[Unit]("generate-services")
            .text("Generate Service resources. When provided, Ingress and Pod Controller resource types are not generated unless explicitly requested")
            .action(KubernetesArgs.set((_, args) => args.copy(generateServices = true))),

          opt[String]("ingress-annotation")
            .text("Adds an annotation to the generated Ingress resources. Format: NAME=value")
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

          opt[String]("ingress-api-version")
            .text(s"Sets the Ingress API version. Default: ${KubernetesArgs.DefaultIngressApiVersion}")
            .optional()
            .action(IngressArgs.set((v, args) => args.copy(apiVersion = Future.successful(v)))),

          opt[String]("ingress-path-suffix")
            .text("Appends the expression specified to the paths of the generated Ingress resources")
            .action(IngressArgs.set((v, c) => c.copy(pathAppend = Some(v)))),

          opt[Unit]("join-existing-akka-cluster")
            .text("When provided, the pod controller will only join an already formed Akka Cluster")
            .action(GenerateDeploymentArgs.set((_, args) => args.copy(joinExistingAkkaCluster = true))),

          opt[String]("namespace")
            .text("Resources will be generated with the supplied namespace")
            .action(KubernetesArgs.set((v, args) => args.copy(namespace = Some(v)))),

          opt[String]('o', "output")
            .text("Specify the output directory for the generated resources")
            .optional()
            .action(KubernetesArgs.set((v, args) => args.copy(output = KubernetesArgs.Output.SaveToFile(v)))),

          opt[String]("pod-controller-api-version")
            .text(s"Sets the Pod Controller (e.g. Deployment) API version. Default: ${KubernetesArgs.DefaultPodControllerApiVersion}")
            .optional()
            .action(PodControllerArgs.set((v, args) => args.copy(apiVersion = Future.successful(v)))),

          opt[ImagePullPolicy.Value]("pod-controller-image-pull-policy")
            .text(s"Sets the Docker image pull policy for Pod Controller resources. Supported: ${Deployment.ImagePullPolicy.values.mkString(", ")}")
            .action(PodControllerArgs.set((v, args) => args.copy(imagePullPolicy = v))),

          opt[Int]("pod-controller-replicas")
            .text("Sets the number of replicas for the Pod Controller resources. If Akka Cluster Bootstrap is enabled, this must be set to 2 or greater unless `--join-existing-akka-cluster` is provided")
            .validate(v => if (v >= 0) success else failure("Number of replicas must be zero or more"))
            .action(PodControllerArgs.set((v, args) => args.copy(numberOfReplicas = v))),

          opt[Unit]("registry-disable-https") /* note: this argument will apply for other targets */
            .text("Disables HTTPS when accessing docker registry")
            .optional()
            .action(GenerateDeploymentArgs.set((_, c) => c.copy(registryUseHttps = false))),

          opt[Unit]("registry-disable-tls-validation") /* note: this argument will apply for other targets */
            .text("Disables TLS cert validation when accessing docker registry through HTTPS")
            .optional()
            .action(GenerateDeploymentArgs.set((_, c) => c.copy(registryValidateTls = false))),

          opt[String]("registry-username") /* note: this argument will apply for other targets */
            .text("Specify username to access docker registry. Password must be specified also.")
            .optional()
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(registryUsername = Some(v)))),

          opt[String]("registry-password") /* note: this argument will apply for other targets */
            .text("Specify password to access docker registry. Username must be specified also.")
            .optional()
            .action(GenerateDeploymentArgs.set((v, c) => c.copy(registryPassword = Some(v)))),

          opt[String]("service-api-version")
            .text(s"Sets the Service API version. Default: ${KubernetesArgs.DefaultServiceApiVersion}")
            .optional()
            .action(ServiceArgs.set((v, args) => args.copy(apiVersion = Future.successful(v)))),

          opt[String]("service-cluster-ip")
            .text("Sets the cluster IP for Service resources")
            .action(ServiceArgs.set((v, args) => args.copy(clusterIp = Some(v)))),

          opt[String]("version")
            .text("Uses specified version tag for generated resources instead of version in the docker image")
            .optional()
            .action(GenerateDeploymentArgs.set((v, args) => args.copy(version = Some(v)))),

          opt[String]("transform-ingress")
            .text("A jq expression that will be applied to Ingress resources. jq must be installed")
            .action(KubernetesArgs.set((v, args) => args.copy(transformIngress = Some(v)))),

          opt[String]("transform-namespaces")
            .text("A jq expression that will be applied to Namespace resources. jq must be installed")
            .action(KubernetesArgs.set((v, args) => args.copy(transformNamespaces = Some(v)))),

          opt[String]("transform-pod-controllers")
            .text("A jq expression that will be applied to Pod Controller resources. jq must be installed")
            .action(KubernetesArgs.set((v, args) => args.copy(transformPodControllers = Some(v)))),

          opt[String]("transform-services")
            .text("A jq expression that will be applied to Service resources. jq must be installed")
            .action(KubernetesArgs.set((v, args) => args.copy(transformServices = Some(v)))))

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

    def mergeWithEnvs(inputArgs: InputArgs, envs: Map[String, String]): InputArgs = {
      val tlsCacertsPathEnv = envs.get(RP_CAINFO)
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
  tlsCacertsPath: Option[String] = None,
  commandArgs: Option[CommandArgs] = None)
