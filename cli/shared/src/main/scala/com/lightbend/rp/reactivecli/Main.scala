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

package com.lightbend.rp.reactivecli

import com.lightbend.rp.reactivecli.argparse.kubernetes.KubernetesArgs
import com.lightbend.rp.reactivecli.argparse.marathon.MarathonArgs
import com.lightbend.rp.reactivecli.argparse.{ GenerateDeploymentArgs, InputArgs, VersionArgs }
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.{ Config, DockerCredentials, DockerEngine, DockerRegistry }
import com.lightbend.rp.reactivecli.process.jq
import com.lightbend.rp.reactivecli.runtime.kubernetes
import com.lightbend.rp.reactivecli.runtime.marathon
import com.lightbend.rp.reactivecli.http.{ Http, HttpRequest, HttpSettings }
import com.lightbend.rp.reactivecli.http.Http.HttpExchange
import scala.annotation.tailrec
import scala.concurrent.Future
import scalaz._
import slogging._

import Scalaz._

trait MinVersion {
  val name: String
  val major: Int
  val minor: Int
  val minimum: String
  val label: String
}

object MinSupportedSbtReactiveApp extends MinVersion {
  val name = "sbt-reactive-app"
  val major: Int = 1
  val minor: Int = 1
  val minimum = s"$major.$minor.0"
  val label = "com.lightbend.rp.sbt-reactive-app-version"
}

object MinSupportedReactiveMavenApp extends MinVersion {
  val name = "reactive-maven-app-plugin"
  val major: Int = 0
  val minor: Int = 2
  val minimum = s"$major.$minor.0"
  val label = "com.lightbend.rp.reactive-maven-app-version"
}

/**
 * This is the main entry of the Reactive CLI.
 */
object Main extends LazyLogging {
  val CliName = "reactive-cli"

  val parser = InputArgs.parser(CliName, ProgramVersion.current)

  def parseVersion(version: String): Option[(Int, Int, Int)] = {
    // Only strings like "1.2.3" are supported, what comes after
    // doesn't matter so snapshots like "0.1.2-SNAPSHOT" are okay.
    try {
      val parts = version.split("-|\\.")
      if (parts.length < 3)
        None
      else
        Some(parts(0).toInt, parts(1).toInt, parts(2).toInt)
    } catch {
      case _: Exception => None
    }
  }

  def isVersionValid(version: String, reqMajor: Int, reqMinor: Int): Boolean = {
    parseVersion(version) match {
      case Some((givenMajor, givenMinor, _)) => {
        if (givenMajor == reqMajor)
          givenMinor >= reqMinor
        else
          givenMajor >= reqMajor
      }
      case None => false
    }
  }

  private def validateBuildPluginVersion(config: Config, plugin: MinVersion): Future[Config] = {
    val maybePluginVersion = config.config.Labels.flatMap(_.get(plugin.label))
    val maybeValid = maybePluginVersion.map(isVersionValid(_, plugin.major, plugin.minor))

    // Only succeed if plugin version is defined and it's not too old
    maybeValid match {
      case None => {
        val msg = s"build plugin label not found; docker image must be built using sbt-reactive-app or reactive-app-maven-plugin"
        Future.failed(new IllegalArgumentException(msg))
      }
      case Some(false) => {
        val msg = s"minimum ${plugin.name} version is ${plugin.minimum}, docker image was built using ${maybePluginVersion.getOrElse("")}"
        Future.failed(new IllegalArgumentException(msg))
      }
      case Some(true) => Future.successful(config)
    }
  }

  private def homeDirPath(subdir: String, filename: String): Option[String] =
    for {
      home <- sys.props.get("user.home")
      path = files.pathFor(home, subdir, filename)
      if files.fileExists(path)
    } yield path

  @tailrec
  private def run(args: Array[String]): Unit = {
    if (args.nonEmpty) {
      val parsedArgs = parser.parse(args, InputArgs.default)

      if (parsedArgs.isEmpty) {
        System.exit(1)
      } else {
        parsedArgs.foreach { inputArgs =>
          import scalaz.Validation.FlatMap._

          val inputArgsMerged = InputArgs.Envs.mergeWithEnvs(inputArgs, environment)

          LoggerConfig.level = inputArgsMerged.logLevel

          inputArgsMerged.commandArgs
            .collect {
              case VersionArgs =>
                System.out.println(s"rp (Reactive CLI) ${ProgramVersion.current}")

                jq.available.foreach { jqAvail =>
                  System.out.println(s"jq support: ${if (jqAvail) "Available" else "Unavailable"}")
                }

              case generateDeploymentArgs @ GenerateDeploymentArgs(_, _, _, _, _, _, _, _, _, _, _, Some(targetRuntimeArgs), _, _, _, _, _) =>
                implicit val httpSettings: HttpSettings =
                  inputArgs.tlsCacertsPath.fold(HttpSettings.default)(v => HttpSettings.default.copy(tlsCacertsPath = Some(v)))

                val http: HttpExchange = Http.http

                def dockerRegistryArgsAuth =
                  for {
                    username <- generateDeploymentArgs.registryUsername
                    password <- generateDeploymentArgs.registryPassword
                  } yield HttpRequest.BasicAuth(username, password)

                def dockerRegistryAuth(imageName: String) = {
                  val configFile = homeDirPath(".docker", "config.json")
                  val credsFile = homeDirPath(".lightbend", "docker.credentials")

                  for {
                    creds <- DockerCredentials.get(credsFile, configFile)
                  } yield {
                    val dockerRegistryFileAuth =
                      for {
                        registry <- DockerRegistry.getRegistry(imageName)
                        entry <- creds.find(realm => docker.registryAuthNameMatches(registry, realm.registry))
                      } yield entry.credentials match {
                        case Left(raw) => HttpRequest.EncodedBasicAuth(raw)
                        case Right((username, password)) => {
                          if (username == "oauth2accesstoken")
                            HttpRequest.BearerToken(password)
                          else
                            HttpRequest.BasicAuth(username, password)
                        }

                      }

                    val dockerRegistryAuth = dockerRegistryArgsAuth.orElse(dockerRegistryFileAuth)

                    dockerRegistryAuth match {
                      case None =>
                        logger.debug("Attempting to pull manifest while unauthenticated")
                      case Some(HttpRequest.BasicAuth(username, _)) =>
                        logger.debug(s"Attempting to pull manifest as $username")
                      case Some(HttpRequest.EncodedBasicAuth(_)) =>
                        logger.debug("Attempting to pull manifest with encoded basic auth (config.json)")
                      case Some(HttpRequest.BearerToken(t)) =>
                        logger.debug("Attempting to pull manifest with bearer token authentication")
                    }

                    dockerRegistryAuth
                  }
                }

                def getDockerHostConfig(imageName: String): Future[Option[Config]] = {
                  implicit val httpSettingsWithDockerCredentials: HttpSettings = DockerEngine.applyDockerHostSettings(httpSettings, environment)
                  val http = Http.http(httpSettingsWithDockerCredentials)
                  DockerEngine
                    .getConfigFromDockerHost(http, environment)(imageName)(httpSettingsWithDockerCredentials)
                    .map(_.map(_.registryConfig))
                }

                def getDockerRegistryConfig(imageName: String): Future[Config] =
                  dockerRegistryAuth(imageName).flatMap { creds =>
                    DockerRegistry.getConfig(
                      http,
                      creds,
                      generateDeploymentArgs.registryUseHttps,
                      generateDeploymentArgs.registryValidateTls, imageName).map(_._1)
                  }

                def getDockerConfig(imageName: String): Future[Config] = {
                  def validateConfig(config: Config): Future[Config] = {
                    val sbtConfig = validateBuildPluginVersion(config, MinSupportedSbtReactiveApp)
                    val mavenConfig = validateBuildPluginVersion(config, MinSupportedReactiveMavenApp)

                    sbtConfig fallbackTo mavenConfig
                  }

                  process.docker.inspectImageForConfig(imageName).flatMap(_.map(Future.successful).getOrElse(
                    getDockerHostConfig(imageName).flatMap(_.map(Future.successful).getOrElse(
                      getDockerRegistryConfig(imageName)))))
                    .flatMap(validateConfig _)
                }

                def configFailure(img: String, t: Throwable) = {
                  if (inputArgsMerged.stackTrace)
                    t.printStackTrace()
                  s"Failed to obtain Docker config for $img, ${t.getMessage}"
                }

                Future
                  .sequence(generateDeploymentArgs.dockerImages.map(img => attempt(getDockerConfig(img)).map(c => img -> c)))
                  .flatMap { tryConfigs =>
                    val (failures, successes) = tryConfigs.partition(_._2.isFailure)

                    if (failures.isEmpty) {
                      targetRuntimeArgs match {
                        case kubernetesArgs: KubernetesArgs =>
                          val futureValidationResources =
                            successes.map {
                              case (image, tryConfig) =>
                                kubernetes.generateResources(image, tryConfig.get, generateDeploymentArgs, kubernetesArgs)
                            }

                          Future
                            .sequence(futureValidationResources)
                            .map { validations =>
                              validations
                                .foldLeft(Vector.empty[kubernetes.GeneratedKubernetesResource].successNel[String]) {
                                  case (acc, v) => (acc |@| v)(_ ++ _)
                                }
                                .flatMap(kubernetes.mergeGeneratedResources(kubernetesArgs, _))
                                .map(resources => kubernetes.handleGeneratedResources(kubernetesArgs.output)(resources))
                            }
                            .recover { case t: Throwable => s"Failed to generate Kubernetes resources for ${generateDeploymentArgs.dockerImages.mkString(", ")}, ${t.getMessage}".failureNel }
                        case marathonArgs: MarathonArgs =>
                          val futureConfiguration =
                            marathon.generateConfiguration(
                              successes.map(t => t._1 -> t._2.get),
                              generateDeploymentArgs,
                              marathonArgs)

                          futureConfiguration.map { validations =>
                            validations.map(config => marathon.outputConfiguration(config, marathonArgs.output))
                          }
                      }
                    } else {
                      val failureMessages =
                        failures
                          .map { case (img, f) => configFailure(img, f.failed.get) }
                      Future.successful(NonEmptyList(failureMessages.head, failureMessages.tail: _*).failure)
                    }
                  }
                  .foreach { validation =>
                    validation.fold(
                      { errors =>
                        errors
                          .stream
                          .toVector
                          .distinct
                          .foreach(logger.error(_))

                        System.exit(1)
                      },
                      whenDone =>
                        whenDone.onComplete {
                          case v if v.isSuccess => System.exit(0)
                          case v => System.exit(2)
                        })
                  }
            }
        }
      }
    } else {
      run(Array("--help"))
    }
  }

  def main(args: Array[String]): Unit = {
    start()

    try {
      run(calculateArgs(args))
    } finally {
      stop()
    }
  }
}
