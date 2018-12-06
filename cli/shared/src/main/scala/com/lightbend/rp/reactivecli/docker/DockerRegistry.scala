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
    encodeURI(s"${protocol(useHttps)}://${img.url}/v2/${img.namespace.fold("")(n => s"$n/")}${img.image}/blobs/$digest")

  private[docker] def manifestUrl(img: Image, useHttps: Boolean): String =
    encodeURI(s"${protocol(useHttps)}://${img.url}/v2/${img.namespace.fold("")(n => s"$n/")}${img.image}/manifests/${img.ref.value}")

  private[docker] def tagsUrl(img: Image, useHttps: Boolean): String =
    encodeURI(s"${protocol(useHttps)}://${img.url}/v2/${img.namespace.fold("")(n => s"$n/")}${img.image}/tags/list")

  private def tokenUrl(realm: String, service: String, scope: Option[String], clientId: String) =
    encodeURI(s"$realm?service=$service&client_id=$clientId${scope.fold("")(s => s"&scope=$s")}")

  private def protocol(useHttps: Boolean): String =
    if (useHttps) "https" else "http"

  private[docker] def parseImageUri(uri: String): Try[Image] = {
    if (uri == "") Failure(new IllegalArgumentException(s"""Cannot parse a blank uri"""))
    else {
      val parts = uri.split("/", 3).toVector
      val partsZero = parts(0)
      val partsZeroIsHost = partsZero.contains(":") || partsZero.contains(".")
      val providedUrl = (parts.length > 2 || (parts.length > 1 && partsZeroIsHost)).option(partsZero)

      val providedNs = (parts.length > 2).option(parts(1))
        .orElse((parts.length > 1 && !partsZeroIsHost).option(partsZero))

      val imageWithTagOrDigest = (parts.length > 2).option(parts(2))
        .orElse((parts.length > 1).option(parts(1)))
        .getOrElse(partsZero)

      val firstColon = imageWithTagOrDigest.indexOf(":")

      val firstAt = imageWithTagOrDigest.indexOf("@")

      val (image, providedRef) =
        if (firstColon >= 0 && firstAt >= 0)
          imageWithTagOrDigest.take(firstAt) -> Some(ImageDigest(imageWithTagOrDigest.substring(firstAt + 1)))
        else if (firstColon >= 0)
          imageWithTagOrDigest.take(firstColon) -> Some(ImageTag(imageWithTagOrDigest.substring(firstColon + 1)))
        else
          imageWithTagOrDigest -> None

      if (image.isEmpty || providedRef.fold(false)(_.value.isEmpty))
        Failure(new IllegalArgumentException(s"""Cannot parse uri "$uri"""))
      else {
        Success {
          Image(
            url = providedUrl.getOrElse(DockerDefaultRegistry),
            namespace = providedNs.orElse(providedUrl.fold(DockerDefaultLibrary.some)(_ => None)),
            image = image,
            ref = providedRef.getOrElse(ImageTag(DockerDefaultTag)),
            providedUrl = providedUrl,
            providedNamespace = providedNs,
            providedImage = image,
            providedRef = providedRef)
        }
      }
    }
  }

  private def getBlob(http: HttpExchange, credentials: Option[HttpRequest.Auth], useHttps: Boolean, validateTls: Boolean, img: Image, digest: String): Future[(HttpResponse, Option[HttpRequest.Auth])] =
    for {
      r <- getWithToken(http, credentials, validateTls, blobUrl(img, digest, useHttps), HttpHeaders(Map.empty), true, Some(img.pullScope))
    } yield r

  private def checkRepositoryValid(http: HttpExchange, credentials: Option[HttpRequest.Auth], useHttps: Boolean, validateTls: Boolean, img: Image)(implicit settings: HttpSettings): Future[(Either[String, Unit], Option[HttpRequest.Auth])] =
    for {
      // We only fetch a single tag (?n=1) because we only care about status codes, and this saves data transfer for
      // large repositories.

      r <- getWithToken(http, credentials, validateTls, s"${tagsUrl(img, useHttps)}?n=1", HttpHeaders(Map()), true, Some(img.pullScope))
    } yield (
      r._1.statusCode match {
        case x if x >= 200 && x <= 299 => Right(())
        case 401 => Left("unable to access repository or registry; check authentication [401]")
        case c => Left(s"unable to find repository or registry [$c]")
      },
      r._2)

  def getConfig(http: HttpExchange, credentials: Option[HttpRequest.Auth], useHttps: Boolean, validateTls: Boolean, uri: String)(implicit settings: HttpSettings): Future[(Config, Option[HttpRequest.Auth])] =
    for {
      img <- Future.fromTry(parseImageUri(uri))
      _ = logger.debug("Image: {}", img)
      validRepository <- checkRepositoryValid(http, credentials, useHttps, validateTls, img)
      _ <- validRepository._1 match {
        case Left(errorMessage) => {
          logger.debug(s"checkRepositoryValid failed: $errorMessage")
          Future.failed(new IllegalArgumentException(errorMessage))
        }
        case Right(_) => Future.successful(())
      }
      token = validRepository._2
      failureMessages = Map(
        404L -> s"unable to find image with ${img.ref.name} ${img.ref.value}",
        401L -> "unable to access image; check authentication")
      manifest <- getManifest(http, token, useHttps, validateTls, img, failureMessages)
      blob <- getBlob(http, token, useHttps, validateTls, img, manifest._1.config.digest)
      config <- Future.fromTry(getDecoded[Config](blob._1, Map.empty))
    } yield config -> blob._2

  def getRegistry(image: String): Option[String] =
    parseImageUri(image)
      .toOption
      .map(_.url)

  private def getManifest(http: HttpExchange, credentials: Option[HttpRequest.Auth], useHttps: Boolean, validateTls: Boolean, img: Image, failureMessages: Map[Long, String]): Future[(Manifest, Option[HttpRequest.Auth])] =
    for {
      r <- getWithToken(http, credentials, validateTls, manifestUrl(img, useHttps), HttpHeaders(Map("Accept" -> DockerAcceptManifestHeader)), true, Some(img.pullScope))
      v <- Future.fromTry(getDecoded[Manifest](r._1, failureMessages))
    } yield v -> r._2

  private def getDecoded[T](response: HttpResponse, failureMessages: Map[Long, String])(implicit decode: DecodeJson[T]): Try[T] =
    if (response.statusCode == 200L)
      response.body.getOrElse("").decodeEither[T].fold(
        err => Failure(new IllegalArgumentException(s"Decode Failure: $err")),
        Success.apply)
    else
      Failure(
        new IllegalArgumentException(
          failureMessages.getOrElse(
            response.statusCode,
            s"expected code 200, received ${response.statusCode}")))

  private def getWithToken(http: HttpExchange, credentials: Option[HttpRequest.Auth], validateTls: Boolean, url: String, headers: HttpHeaders, tryNewToken: Boolean = true, fallbackScope: Option[String]): Future[(HttpResponse, Option[HttpRequest.Auth])] = {
    logger.debug("Request URL: {}", url)

    val base = HttpRequest(url).headers(headers).
      copy(tlsValidationEnabled = Some(validateTls))

    val request = credentials match {
      case Some(token: HttpRequest.BearerToken) =>
        base.headers(headers.updated("Authorization", s"Bearer ${token.value}"))
      case Some(auth: HttpRequest.BasicAuth) =>
        base.withAuth(auth)
      case Some(auth: HttpRequest.EncodedBasicAuth) =>
        base.withAuth(auth)
      case _ => base
    }

    http.apply(request).flatMap {
      case response if response.statusCode == 401 && response.headers.contains("Www-Authenticate") && tryNewToken =>
        logger.debug("Received 401 from Registry, attempting to get a token and try again")

        val authenticateHeader = response.headers("Www-Authenticate")

        val maybeResponse =
          for {
            auth <- optionToFuture(parseAuthHeader(authenticateHeader), "Unable to parse authentication header")
            realm <- optionToFuture(auth.get("Bearer realm"), "Missing realm")
            service <- optionToFuture(auth.get("service"), "Missing service")
            tokenRequest = HttpRequest(tokenUrl(realm, service, auth.get("scope").orElse(fallbackScope), "LightbendReactiveCLI"))
            maybeTokenResponse <- attempt(http(credentials.fold(tokenRequest)(tokenRequest.withAuth))).map(_.toOption)

            if maybeTokenResponse.exists(_.statusCode == 200)

            tokenResponse = maybeTokenResponse.get

            body <- optionToFuture(tokenResponse.body, "Missing body")
            json <- optionToFuture(JsonParser.parse(body).right.toOption, "Cannot parse body as JSON")
            token <- optionToFuture(json.field("token"), "Missing token")
            tokenStr <- optionToFuture(token.string, "Token not a string")
            result <- getWithToken(http, Some(HttpRequest.BearerToken(tokenStr)), validateTls, url, headers, tryNewToken = false, fallbackScope = fallbackScope)
          } yield result

        maybeResponse.recover {
          case t: Throwable =>
            logger.trace("Error: {}", t)
            logger.error(s"Unable to obtain an OAuth token (${response.statusCode}${response.body.fold("")(" " + _)})")

            response -> credentials
        }

      case response =>
        logger.debug(s"Received ${response.statusCode} from Registry")

        Future.successful(response -> credentials)
    }
  }
}
