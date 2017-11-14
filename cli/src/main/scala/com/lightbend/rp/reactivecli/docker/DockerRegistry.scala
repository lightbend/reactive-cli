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
package docker

import argonaut._
import libhttpsimple._

import scala.util.{ Failure, Success, Try }
import scalaz._
import slogging._
import Argonaut._
import libhttpsimple.LibHttpSimple.HttpExchange

import Scalaz._

object DockerRegistry extends LazyLogging {
  private[docker] def blobUrl(img: Image, digest: String, useHttps: Boolean): String =
    s"${protocol(useHttps)}://${img.url}/v2/${img.namespace}/${img.image}/blobs/$digest"

  private[docker] def manifestUrl(img: Image, useHttps: Boolean): String =
    s"${protocol(useHttps)}://${img.url}/v2/${img.namespace}/${img.image}/manifests/${img.tag}"

  private def protocol(useHttps: Boolean): String =
    if (useHttps) "https" else "http"

  private[docker] def parseImageUri(uri: String): Try[Image] = {
    val parts = uri.split("/", 3).toVector

    val providedUrl = (parts.length > 2).option(parts(0))

    val providedNs = (parts.length > 2).option(parts(1))
      .orElse((parts.length > 1).option(parts(0)))

    val imageParts = (parts.length > 2).option(parts(2))
      .orElse((parts.length > 1).option(parts(1)))
      .getOrElse(parts(0))
      .split(":", 2)

    val image = imageParts(0)

    val providedTag = (imageParts.length > 1).option(imageParts(1))

    if (image.isEmpty || providedTag.fold(false)(_.isEmpty))
      Failure(new IllegalArgumentException(s"""Cannot parse uri "$uri"""))
    else
      Success(
        Image(
          url = providedUrl.getOrElse(DockerDefaultRegistry),
          namespace = providedNs.getOrElse(DockerDefaultLibrary),
          image = image,
          tag = providedTag.getOrElse(DockerDefaultTag),
          providedUrl = providedUrl,
          providedNamespace = providedNs,
          providedImage = image,
          providedTag = providedTag))
  }

  private def getBlob(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], useHttps: Boolean, validateTls: Boolean)(uri: String, digest: String, token: Option[HttpRequest.BearerToken]): Try[(HttpResponse, Option[HttpRequest.BearerToken])] =
    for {
      i <- parseImageUri(uri)
      r <- getWithToken(http, credentials, validateTls)(blobUrl(i, digest, useHttps), HttpHeaders(Map.empty), token = token)
    } yield r

  def getConfig(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], useHttps: Boolean, validateTls: Boolean)(uri: String, token: Option[HttpRequest.BearerToken])(implicit settings: LibHttpSimple.Settings): Try[(Config, Option[HttpRequest.BearerToken])] =
    for {
      manifest <- getManifest(http, credentials, useHttps, validateTls)(uri, token)
      blob <- getBlob(http, credentials, useHttps, validateTls)(uri, manifest._1.config.digest, token = manifest._2)
      config <- getDecoded[Config](blob._1)
    } yield config -> blob._2

  private def getManifest(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], useHttps: Boolean, validateTls: Boolean)(uri: String, token: Option[HttpRequest.BearerToken]): Try[(Manifest, Option[HttpRequest.BearerToken])] =
    for {
      i <- parseImageUri(uri)
      r <- getWithToken(http, credentials, validateTls)(manifestUrl(i, useHttps), HttpHeaders(Map("Accept" -> DockerAcceptManifestHeader)), token = token)
      v <- getDecoded[Manifest](r._1)
    } yield v -> r._2

  private def getDecoded[T](response: HttpResponse)(implicit decode: DecodeJson[T]): Try[T] =
    if (response.statusCode == 200)
      response.body.getOrElse("").decodeEither[T].fold(
        err => Failure(new IllegalArgumentException(s"Decode Failure: $err")),
        Success.apply)
    else
      Failure(new IllegalArgumentException(s"Expected code 200, received ${response.statusCode}"))

  private def getWithToken(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], validateTls: Boolean)(url: String, headers: HttpHeaders, tryNewToken: Boolean = true, token: Option[HttpRequest.BearerToken] = None): Try[(HttpResponse, Option[HttpRequest.BearerToken])] = {
    val request =
      HttpRequest(url)
        .headers(token.fold(headers)(t => headers.updated("Authorization", s"Bearer $t")))
        .copy(tlsValidationEnabled = Some(validateTls))

    http.apply(request).flatMap {
      case response if response.statusCode == 401 && response.headers.contains("Www-Authenticate") && tryNewToken =>
        logger.debug("Received 401, attempting to get a token and try again")

        val authenticateHeader = response.headers("Www-Authenticate")

        val maybeResponse =
          for {
            realm <- parseWwwAuthenticate(authenticateHeader, "realm")
            service <- parseWwwAuthenticate(authenticateHeader, "service")
            scope <- parseWwwAuthenticate(authenticateHeader, "scope")
            tokenRequest = HttpRequest(tokenUrl(realm, service, scope, "LightbendReactiveCLI"))
            tokenResponse <- http(credentials.fold(tokenRequest)(tokenRequest.withAuth)).toOption

            if tokenResponse.statusCode == 200

            body <- tokenResponse.body
            json <- JsonParser.parse(body).right.toOption
            token <- json.field("token")
            tokenStr <- token.string
          } yield getWithToken(http, credentials, validateTls)(url, headers, tryNewToken = false, token = Some(HttpRequest.BearerToken(tokenStr)))

        maybeResponse.getOrElse {
          logger.error(s"Unable to obtain an OAuth token (${response.statusCode}${response.body.fold("")(" " + _)})")
          logger.debug(response.toString)
          Success(response -> token)
        }

      case response =>
        Success(response -> token)
    }
  }

  /* @TODO need a URL query library */
  private def tokenUrl(realm: String, service: String, scope: String, clientId: String) =
    s"$realm?service=$service&scope=$scope&client_id=$clientId"

  /* @TODO need a more robust parser */
  def parseWwwAuthenticate(authenticate: String, section: String): Option[String] =
    (section + "=\"([^\"]+)\"")
      .r
      .findFirstMatchIn(authenticate)
      .flatMap(_.subgroups.headOption)
}
