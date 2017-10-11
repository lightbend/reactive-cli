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

import Argonaut._

object DockerRegistry {
  def blobUrl(img: Image, digest: String): String =
    s"https://${img.url}/v2/${img.namespace}/${img.image}/blobs/$digest"

  def manifestUrl(img: Image): String =
    s"https://${img.url}/v2/${img.namespace}/${img.image}/manifests/${img.tag}"

  def parseImageUri(uri: String): Try[Image] = {
    val parts = uri.split("/", 3).toVector

    val providedUrl = someIf(parts.length > 2)(parts(0))

    val providedNs = someIf(parts.length > 2)(parts(1))
      .orElse(someIf(parts.length > 1)(parts(0)))

    val imageParts = someIf(parts.length > 2)(parts(2))
      .orElse(someIf(parts.length > 1)(parts(1)))
      .getOrElse(parts(0))
      .split(":", 2)

    val image = imageParts(0)

    val providedTag = someIf(imageParts.length > 1)(imageParts(1))

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

  def getBlob(uri: String, digest: String): Try[HttpResponse] =
    for {
      i <- parseImageUri(uri)
      r <- getWithToken(blobUrl(i, digest), HttpHeaders(Map.empty))
    } yield r

  def getConfig(uri: String): Try[Config] =
    for {
      manifest <- getManifest(uri)
      blob <- getBlob(uri, manifest.config.digest)
      config <- getDecoded[Config](blob)
    } yield config

  def getDecoded[T](response: HttpResponse)(implicit decode: DecodeJson[T]): Try[T] =
    if (response.statusCode == 200)
      response.body.getOrElse("").decodeEither[T].fold(
        err => Failure(new IllegalArgumentException(s"Decode Failure: $err")),
        Success.apply)
    else
      Failure(new IllegalArgumentException(s"Expected code 200, received ${response.statusCode}"))

  def getManifest(uri: String): Try[Manifest] =
    for {
      i <- parseImageUri(uri)
      r <- getWithToken(manifestUrl(i), HttpHeaders(Map("Accept" -> DockerAcceptManifestHeader)))
      v <- getDecoded[Manifest](r)
    } yield v

  private def getWithToken(url: String, headers: HttpHeaders, tryNewToken: Boolean = true, token: Option[String] = None): Try[HttpResponse] = {
    val request =
      HttpRequest(url)
        .headers(token.fold(headers)(t => headers.updated("Authorization", s"Bearer $t")))
        .enableFollowRedirects

    LibHttpSimple(request).flatMap {
      case response if response.statusCode == 401 && response.headers.contains("Www-Authenticate") && tryNewToken =>
        val authenticateHeader = response.headers("Www-Authenticate")

        val maybeResponse =
          for {
            realm <- parseWwwAuthenticate(authenticateHeader, "realm")
            service <- parseWwwAuthenticate(authenticateHeader, "service")
            scope <- parseWwwAuthenticate(authenticateHeader, "scope")
            tokenResponse <- LibHttpSimple(HttpRequest(tokenUrl(realm, service, scope, "LightbendReactiveCLI"))).toOption

            if tokenResponse.statusCode == 200

            body <- tokenResponse.body
            json <- JsonParser.parse(body).right.toOption
            token <- json.field("token")
            tokenStr <- token.string
          } yield getWithToken(url, headers, tryNewToken = false, token = Some(tokenStr))

        maybeResponse.getOrElse {
          // @TODO log the nature of the failure to debug here

          Success(response)
        }

      case response =>
        Success(response)
    }
  }

  /* @TODO need a URL query library */
  private def tokenUrl(realm: String, service: String, scope: String, clientId: String) =
    s"$realm?service=$service&scope=$scope&client_id=$clientId"

  /* @TODO need a more robust parser */
  private def parseWwwAuthenticate(authenticate: String, section: String) = (section + "=\"([^\"]+)\"")
    .r
    .findFirstMatchIn(authenticate)
    .flatMap(_.subgroups.headOption)
}
