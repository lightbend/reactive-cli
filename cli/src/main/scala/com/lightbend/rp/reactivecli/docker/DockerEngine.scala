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
import libhttpsimple._
import java.nio.file.{ Files, Paths }

import Argonaut._

import scala.util.{ Failure, Success, Try }

object DockerEngine {
  def applyDockerHostSettings(settings: LibHttpSimple.Settings, env: Map[String, String]): LibHttpSimple.Settings = {
    val credentialFiles = for {
      certDir <- env.get("DOCKER_CERT_PATH")

      keyFile = Paths.get(certDir, "key.pem")
      certFile = Paths.get(certDir, "cert.pem")
      caFile = Paths.get(certDir, "ca.pem")

      if Files.exists(keyFile) && Files.exists(certFile) && Files.exists(caFile)
    } yield (keyFile, certFile, caFile)

    credentialFiles.fold(settings) { v =>
      val (keyFile, certFile, caFile) = v
      settings.copy(
        tlsCacertsPath = Some(caFile),
        tlsCertPath = Some(certFile),
        tlsKeyPath = Some(keyFile))
    }
  }

  def getConfigFromDockerHost(http: LibHttpSimple.HttpExchange, env: Map[String, String])(uri: String)(implicit settings: LibHttpSimple.Settings): Option[SocketConfig] = {
    for {
      host <- env.get("DOCKER_HOST")
      verify = env.get("DOCKER_TLS_VERIFY").contains("1")

      if host.startsWith("tcp://")

      protocol = if (verify) "https" else "http"
      url = s"$protocol://${host.replaceFirst("tcp://", "")}/images/$uri/json"

      response <- http(HttpRequest(url)).toOption

      config <- getDecoded[SocketConfig](response).toOption
    } yield config
  }

  private def getDecoded[T](response: HttpResponse)(implicit decode: DecodeJson[T]): Try[T] = {
    if (response.statusCode == 200)
      response.body.getOrElse("").decodeEither[T].fold(
        err => Failure(new IllegalArgumentException(s"Decode Failure: $err")),
        Success.apply)
    else
      Failure(new IllegalArgumentException(s"Expected code 200, received ${response.statusCode}${response.body.fold("")(": " + _)}"))
  }
}
