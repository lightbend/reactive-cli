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
import com.lightbend.rp.reactivecli.argparse.{ GenerateDeploymentArgs, InputArgs, VersionArgs }
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.{ Config, DockerCredentials, DockerEngine, DockerRegistry }
import com.lightbend.rp.reactivecli.process.jq
import com.lightbend.rp.reactivecli.runtime.kubernetes
import com.lightbend.rp.reactivecli.http.{ Http, HttpRequest, HttpSettings }
import com.lightbend.rp.reactivecli.http.Http.HttpExchange
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import scalaz._
import slogging._

import Scalaz._

/**
 * This is the main entry of the Reactive CLI.
 */
object Main extends LazyLogging {
  val CliName = "reactive-cli"

  val parser = InputArgs.parser(CliName, ProgramVersion.current)

  object MinSupportedSbtReactiveApp {
    val major: Int = 0
    val minor: Int = 4
    val minimum = s"$major.$minor.0"

    def parseVersion(version: String): Option[(Int, Int, Int)] = {
      // Only strings like "1.2.3" are supported, what comes after
      // doesn't matter so snapshots like "0.1.2-SNAPSHOT" are okay.
      try {
        val parts = version.split("-|\\.")
        Some(parts(0).toInt, parts(1).toInt, parts(2).toInt)
      } catch {
        case _: Exception => None
      }
    }

    def isVersionValid(version: String, reqMajor: Int = major, reqMinor: Int = minor): Boolean = {
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
      parser.parse(args, InputArgs.default).foreach { inputArgs =>
        val inputArgsMerged = InputArgs.Envs.mergeWithEnvs(inputArgs, environment)

        LoggerConfig.level = inputArgsMerged.logLevel

        inputArgsMerged.commandArgs
          .collect {
            case VersionArgs =>
              System.out.println(s"rp (Reactive CLI) ${ProgramVersion.current}")

              jq.available.foreach { jqAvail =>
                System.out.println(s"jq support: ${if (jqAvail) "Available" else "Unavailable"}")
              }

            case generateDeploymentArgs @ GenerateDeploymentArgs(_, _, _, _, _, _, _, _, _, _, Some(kubernetesArgs: KubernetesArgs), _, _, _, _, _) =>
              implicit val httpSettings: HttpSettings =
                inputArgs.tlsCacertsPath.fold(HttpSettings.default)(v => HttpSettings.default.copy(tlsCacertsPath = Some(v)))

              val http: HttpExchange = Http.http

              val dockerRegistryArgsAuth =
                for {
                  username <- generateDeploymentArgs.registryUsername
                  password <- generateDeploymentArgs.registryPassword
                } yield HttpRequest.BasicAuth(username, password)

              val configFile = homeDirPath(".docker", "config.json")
              val credsFile = homeDirPath(".lightbend", "docker.credentials")
              val dockerCredentials =
                for {
                  creds <- DockerCredentials.get(credsFile, configFile)
                } yield {
                  val dockerRegistryFileAuth =
                    for {
                      imageName <- generateDeploymentArgs.dockerImage
                      registry <- DockerRegistry.getRegistry(imageName)
                      entry <- creds.find(realm => docker.registryAuthNameMatches(registry, realm.registry))
                    } yield entry.credentials match {
                      case Left(raw) => HttpRequest.EncodedBasicAuth(raw)
                      case Right((username, password)) => HttpRequest.BasicAuth(username, password)
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

              def getDockerHostConfig(imageName: String): Future[Option[Config]] = {
                implicit val httpSettingsWithDockerCredentials: HttpSettings = DockerEngine.applyDockerHostSettings(httpSettings, environment)
                val http = Http.http(httpSettingsWithDockerCredentials)
                DockerEngine
                  .getConfigFromDockerHost(http, environment)(imageName)(httpSettingsWithDockerCredentials)
                  .map(_.map(_.registryConfig))
              }

              def getDockerRegistryConfig(imageName: String): Future[Config] =
                dockerCredentials.flatMap { creds =>
                  DockerRegistry.getConfig(
                    http,
                    creds,
                    generateDeploymentArgs.registryUseHttps,
                    generateDeploymentArgs.registryValidateTls)(imageName, token = None).map(_._1)
                }

              def getDockerConfig(imageName: String): Future[Config] = {
                def validateConfig(config: Config): Future[Config] = {
                  val maybeVersion =
                    config
                      .config
                      .Labels
                      .flatMap(_.get("com.lightbend.rp.sbt-reactive-app-version"))

                  val validVersion = maybeVersion.fold(true)(MinSupportedSbtReactiveApp.isVersionValid(_))

                  if (validVersion)
                    Future.successful(config)
                  else
                    Future.failed(
                      new IllegalArgumentException(
                        s"Minimum sbt-reactive-app version is ${MinSupportedSbtReactiveApp.minimum}, given: ${maybeVersion.getOrElse("")}"))
                }

                for {
                  maybeConfig <- getDockerHostConfig(imageName)
                  config <- maybeConfig match {
                    case None => getDockerRegistryConfig(imageName)
                    case Some(c) => Future.successful(c)
                  }
                  validConfig <- validateConfig(config)
                } yield validConfig
              }

              val outputHandler = kubernetes.handleGeneratedResources(kubernetesArgs.output)

              val output =
                attempt(getDockerConfig(generateDeploymentArgs.dockerImage.get))
                  .flatMap {
                    case Failure(t) =>
                      Future.successful(s"Failed to obtain Docker config for ${generateDeploymentArgs.dockerImage.get}, ${t.getMessage}".failureNel)
                    case Success(config) =>
                      kubernetes
                        .generateResources(config, generateDeploymentArgs, kubernetesArgs)
                        .recover { case t: Throwable => s"Failed to generate Kubernetes resources for ${generateDeploymentArgs.dockerImage.get}, ${t.getMessage}".failureNel }
                  }

              output
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
                    resources =>
                      outputHandler(resources).onComplete {
                        case v if v.isSuccess => System.exit(0)
                        case v => System.exit(2)
                      })
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
