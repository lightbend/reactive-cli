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

package com.lightbend.rp.reactivecli.http

import scala.scalanative.native
import scala.util.{ Failure, Success, Try }
import scala.collection.immutable.Seq

object NativeHttp {
  type HttpExchange = HttpRequest => Try[HttpResponse]

  private val CRLF = "\r\n"
  private val HttpHeaderAndBodyPartsSeparator = CRLF + CRLF
  private val HttpHeaderNameAndValueSeparator = ":"

  case class InternalNativeFailure(errorCode: Long, errorDescription: String) extends RuntimeException(s"$errorCode: $errorDescription")

  val defaultSettings = HttpSettings()

  def http(implicit settings: HttpSettings): HttpExchange = apply

  /**
   * Initializes libcurl` internal state by calling `curl_global_init` underneath.
   * This method is _NOT_ thread safe and it's meant to be called at the start of the program.
   */
  def globalInit(): Try[Unit] =
    native.Zone { implicit z =>
      val errorCode = nativebinding.http.global_init()
      if (errorCode.toInt == 0)
        Success(Unit)
      else
        Failure(InternalNativeFailure(-70, "Failure calling curl_global_init"))
    }

  /**
   * Performs cleanup of libcurl` internal state by calling `curl_global_cleanup` underneath.
   * This method is _NOT_ thread safe and it's meant to be called before termination of the program.
   */
  def globalCleanup(): Unit =
    native.Zone { implicit z =>
      nativebinding.http.global_cleanup()
    }

  def apply(request: HttpRequest)(implicit settings: HttpSettings): Try[HttpResponse] =
    doHttp(
      request.requestMethod,
      request.requestUrl,
      request.headersWithAuth.headers,
      request.requestBody,
      request.tlsValidationEnabled,
      Nil)

  private def doHttp(
    method: String,
    url: String,
    headers: Map[String, String],
    requestBody: Option[String],
    tlsValidationEnabled: Option[Boolean],
    visitedUrls: List[String])(implicit settings: HttpSettings): Try[HttpResponse] =
    native.Zone { implicit z =>
      val isTlsValidationEnabled = tlsValidationEnabled.getOrElse(settings.tlsValidationEnabled)

      val response = nativebinding.http.do_http(
        validate_tls = if (isTlsValidationEnabled) 1 else 0,
        method,
        url,
        httpHeadersToDelimitedString(headers),
        requestBody.getOrElse(""),
        settings.tlsCacertsPath.fold("")(_.toString),
        settings.tlsCertPath.fold("")(_.toString),
        settings.tlsKeyPath.fold("")(_.toString))

      response.error match {
        case 0 => {
          val hs = HttpHeaders(parseHeaders(response.header))
          Success(HttpResponse(response.status, hs, response.body))
        }
        case -1 => {
          Failure(InternalNativeFailure(response.error, s"no response from $url"))
        }
        case _ => {
          val msg = nativebinding.http.error_message(response.error)
          Failure(InternalNativeFailure(response.error, msg))
        }
      }
    }

  private def httpHeadersToDelimitedString(headers: Map[String, String]): Seq[String] =
    headers
      .map {
        case (headerName, headerValue) => s"$headerName$HttpHeaderNameAndValueSeparator $headerValue"
      }.toVector

  def parseHeaders(input: Option[String]): Map[String, String] = {
    def splitBySeparator(v: String, separator: String): (String, String) = {
      val lineBreakIndex = v.indexOf(separator)
      val (l, r) = v.splitAt(lineBreakIndex)
      l -> r.substring(separator.length)
    }

    input match {
      case Some(headers) =>
        // Exclude the first line which is the HTTP status line
        val headerLines = headers.split(CRLF).tail

        // Keep track of previous header name to handle multiline fields correctly
        var prev: Option[String] = None
        headerLines.foldLeft(Map.empty[String, String]) { (v, l) =>
          if (l.startsWith(" ") || l.startsWith("\t")) {
            prev match {
              case None => throw new IllegalArgumentException("unexpected whitespace in HTTP header field")
              case Some(key) =>
                val prevVal = v(key)
                v.updated(key, prevVal + " " + l.trim)
            }
          } else {
            val (headerName, headerValue) = splitBySeparator(l, HttpHeaderNameAndValueSeparator)
            prev = Some(headerName)
            v.updated(headerName, headerValue.trim)
          }
        }
      case _ =>
        Map.empty[String, String]
    }
  }
}
