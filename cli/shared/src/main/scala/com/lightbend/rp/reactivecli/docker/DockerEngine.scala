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

package com.lightbend.rp.reactivecli.docker

import argonaut._
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.http._
import scala.concurrent.{ ExecutionContext, Future }
import slogging._

import Argonaut._

object DockerEngine extends LazyLogging {
  def applyDockerHostSettings(settings: HttpSettings, env: Map[String, String]): HttpSettings = {
    val credentialFiles = for {
      certDir <- env.get("DOCKER_CERT_PATH")
      keyFile = pathFor(certDir, "key.pem")
      certFile = pathFor(certDir, "cert.pem")
      caFile = pathFor(certDir, "ca.pem")

      if fileExists(keyFile) && fileExists(certFile) && fileExists(caFile)
    } yield (keyFile, certFile, caFile)

    credentialFiles.fold(settings) { v =>
      val (keyFile, certFile, caFile) = v
      settings.copy(
        tlsCacertsPath = Some(caFile),
        tlsCertPath = Some(certFile),
        tlsKeyPath = Some(keyFile))
    }
  }

  def getConfigFromDockerHost(http: Http.HttpExchange, env: Map[String, String])(uri: String)(implicit settings: HttpSettings): Future[Option[SocketConfig]] =
    env.get("DOCKER_HOST") match {
      case None =>
        Future.successful(None)

      case Some(host) if host.startsWith("tcp://") =>
        val verify = env.get("DOCKER_TLS_VERIFY").contains("1")
        val protocol = if (verify) "https" else "http"
        val url = s"$protocol://${host.replaceFirst("tcp://", "")}/images/$uri/json"

        logger.debug("Attempting to pull config from Engine, {}", url)

        wrapFutureOption(
          for {
            response <- http(HttpRequest(url))

            _ = logger.debug(s"Received {} from Engine", response.statusCode)

            config <- getDecoded[SocketConfig](response)
          } yield config)
    }

  private def getDecoded[T](response: HttpResponse)(implicit decode: DecodeJson[T]): Future[T] = {
    if (response.statusCode == 200)
      response.body.getOrElse("").decodeEither[T].fold(
        err => Future.failed(new IllegalArgumentException(s"Decode Failure: $err")),
        Future.successful)
    else
      Future.failed(new IllegalArgumentException(s"Expected code 200, received ${response.statusCode}${response.body.fold("")(": " + _)}"))
  }
}
