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
import com.lightbend.rp.reactivecli.http._
import com.lightbend.rp.reactivecli.http.Http.HttpExchange
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scalaz._
import slogging._

import Argonaut._
import Scalaz._

object DockerRegistry extends LazyLogging {
  private[docker] def blobUrl(img: Image, digest: String, useHttps: Boolean): String =
    encodeURI(s"${protocol(useHttps)}://${img.url}/v2/${img.namespace}/${img.image}/blobs/$digest")

  private[docker] def manifestUrl(img: Image, useHttps: Boolean): String =
    encodeURI(s"${protocol(useHttps)}://${img.url}/v2/${img.namespace}/${img.image}/manifests/${img.tag}")

  private[docker] def tagsUrl(img: Image, useHttps: Boolean): String =
    encodeURI(s"${protocol(useHttps)}://${img.url}/v2/${img.namespace}/${img.image}/tags/list")

  private def tokenUrl(realm: String, service: String, scope: String, clientId: String) =
    encodeURI(s"$realm?service=$service&scope=$scope&client_id=$clientId")

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

  private def getBlob(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], useHttps: Boolean, validateTls: Boolean)(img: Image, digest: String, token: Option[HttpRequest.BearerToken]): Future[(HttpResponse, Option[HttpRequest.BearerToken])] =
    for {
      r <- getWithToken(http, credentials, validateTls)(blobUrl(img, digest, useHttps), HttpHeaders(Map.empty), token = token)
    } yield r

  private def getTags(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], useHttps: Boolean, validateTls: Boolean)(img: Image, token: Option[HttpRequest.BearerToken])(implicit settings: HttpSettings): Future[(Option[String], Option[HttpRequest.BearerToken])] =
    for {
      r <- getWithToken(http, credentials, validateTls)(tagsUrl(img, useHttps), HttpHeaders(Map()), token = token)
    } yield r._1.body -> r._2

  private def imgValid(tags: Option[String], img: Image): Try[Boolean] = {
    tags match {
      case None => Failure(new IllegalArgumentException(s"got unexpected docker registry response"))
      case Some(str) => {
        val tags = Parse.parseOption(str).flatMap(_.hcursor.downField("tags").focus)
        val foundTag = tags.flatMap(j =>
          j.hcursor.downArray.find(tag => tag.isString && tag.string == Some(img.tag)).focus)
        (tags, foundTag) match {
          case (None, None) => Failure(new IllegalArgumentException(s"image doesn't seem to exist in docker registry"))
          case (Some(_), None) => Failure(new IllegalArgumentException(s"image ${img.image} doesn't have tag named ${img.tag}"))
          case _ => Success(true)
        }
      }
    }
  }

  def getConfig(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], useHttps: Boolean, validateTls: Boolean)(uri: String, token: Option[HttpRequest.BearerToken])(implicit settings: HttpSettings): Future[(Config, Option[HttpRequest.BearerToken])] =
    for {
      img <- Future.fromTry(parseImageUri(uri))
      tags <- getTags(http, credentials, useHttps, validateTls)(img, token)
      valid <- Future.fromTry(imgValid(tags._1, img))
      manifest <- getManifest(http, credentials, useHttps, validateTls)(img, token = tags._2)
      blob <- getBlob(http, credentials, useHttps, validateTls)(img, manifest._1.config.digest, token = tags._2)
      config <- Future.fromTry(getDecoded[Config](blob._1))
    } yield config -> blob._2

  def getRegistry(image: String): Option[String] =
    parseImageUri(image)
      .toOption
      .map(_.url)

  private def getManifest(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], useHttps: Boolean, validateTls: Boolean)(img: Image, token: Option[HttpRequest.BearerToken]): Future[(Manifest, Option[HttpRequest.BearerToken])] =
    for {
      r <- getWithToken(http, credentials, validateTls)(manifestUrl(img, useHttps), HttpHeaders(Map("Accept" -> DockerAcceptManifestHeader)), token = token)
      v <- Future.fromTry(getDecoded[Manifest](r._1))
    } yield v -> r._2

  private def getDecoded[T](response: HttpResponse)(implicit decode: DecodeJson[T]): Try[T] =
    if (response.statusCode == 200)
      response.body.getOrElse("").decodeEither[T].fold(
        err => Failure(new IllegalArgumentException(s"Decode Failure: $err")),
        Success.apply)
    else
      Failure(new IllegalArgumentException(s"Expected code 200, received ${response.statusCode}"))

  private def getWithToken(http: HttpExchange, credentials: Option[HttpRequest.BasicAuth], validateTls: Boolean)(url: String, headers: HttpHeaders, tryNewToken: Boolean = true, token: Option[HttpRequest.BearerToken] = None): Future[(HttpResponse, Option[HttpRequest.BearerToken])] = {
    val request =
      HttpRequest(url)
        .headers(token.fold(headers)(t => headers.updated("Authorization", s"Bearer ${t.value}")))
        .copy(tlsValidationEnabled = Some(validateTls))

    http.apply(request).flatMap {
      case response if response.statusCode == 401 && response.headers.contains("Www-Authenticate") && tryNewToken =>
        logger.debug("Received 401 from Registry, attempting to get a token and try again")

        val authenticateHeader = response.headers("Www-Authenticate")

        val maybeResponse =
          for {
            auth <- optionToFuture(parseAuthHeader(authenticateHeader), "Unable to parse authentication header")
            realm <- optionToFuture(auth.get("Bearer realm"), "Missing realm")
            service <- optionToFuture(auth.get("service"), "Missing service")
            scope <- optionToFuture(auth.get("scope"), "Missing scope")
            tokenRequest = HttpRequest(tokenUrl(realm, service, scope, "LightbendReactiveCLI"))
            maybeTokenResponse <- attempt(http(credentials.fold(tokenRequest)(tokenRequest.withAuth))).map(_.toOption)

            if maybeTokenResponse.exists(_.statusCode == 200)

            tokenResponse = maybeTokenResponse.get

            body <- optionToFuture(tokenResponse.body, "Missing body")
            json <- optionToFuture(JsonParser.parse(body).right.toOption, "Cannot parse body as JSON")
            token <- optionToFuture(json.field("token"), "Missing token")
            tokenStr <- optionToFuture(token.string, "Token not a string")
            result <- getWithToken(http, credentials, validateTls)(url, headers, tryNewToken = false, token = Some(HttpRequest.BearerToken(tokenStr)))
          } yield result

        maybeResponse.recover {
          case t: Throwable =>
            logger.error(s"Unable to obtain an OAuth token (${response.statusCode}${response.body.fold("")(" " + _)})")
            logger.debug(response.toString)

            response -> token
        }

      case response =>
        Future.successful(response -> token)
    }
  }
}
