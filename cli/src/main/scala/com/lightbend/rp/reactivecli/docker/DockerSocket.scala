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
import java.nio.file.Paths
import Argonaut._

import scala.util.{ Failure, Success, Try }

object DockerSocket {
  def getConfigFromUnixSocket(uri: String)(implicit settings: LibHttpSimple.Settings): Option[SocketConfig] = {
    // @TODO escaping for uri?
    for {
      response <- LibHttpSimple(SocketRequest("/var/run/docker.sock", s"http:0/images/$uri/json")).toOption
      config <- getDecoded[SocketConfig](response).toOption
    } yield config
  }

  def getConfigFromDockerHost(uri: String)(implicit settings: LibHttpSimple.Settings): Option[SocketConfig] = {
    for {
      host <- Option(System.getenv("DOCKER_HOST"))
      verify = Option(System.getenv("DOCKER_TLS_VERIFY")).contains("1")
      certs = Option(System.getenv("DOCKER_CERT_PATH"))

      if host.startsWith("tcp://")

      protocol = if (verify) "https" else "http"

      url = s"$protocol://${host.replaceFirst("tcp://", "")}/images/$uri/json"

      newSettings = for {
        certDir <- certs
        keyFile = Paths.get(certDir, "key.pem")
        certFile = Paths.get(certDir, "cert.pem")
        caFile = Paths.get(certDir, "ca.pem")

        if keyFile.toFile.exists()
        if certFile.toFile.exists()
        if caFile.toFile.exists()
      } yield settings.copy(
        tlsCacertsPath = Some(caFile),
        tlsCertPath = Some(certFile),
        tlsKeyPath = Some(keyFile))

      response <- LibHttpSimple(HttpRequest(url))(newSettings.getOrElse(settings)).toOption

      config <- getDecoded[SocketConfig](response).toOption
    } yield config
  }

  private def getDecoded[T](response: HttpResponse)(implicit decode: DecodeJson[T]): Try[T] = {
    if (response.statusCode == 200)
      response.body.getOrElse("").decodeEither[T].fold(
        err => Failure(new IllegalArgumentException(s"Decode Failure: $err")),
        Success.apply)
    else
      Failure(new IllegalArgumentException(s"Expected code 200, received ${response.statusCode}"))
  }
}
