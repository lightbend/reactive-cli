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

package libhttpsimple

import scala.scalanative.native
import scala.scalanative.native._
import scala.util.{ Failure, Success, Try }

object LibHttpSimple {
  private val CRLF = "\r\n"
  private val HttpHeaderAndBodyPartsSeparator = CRLF + CRLF
  private val HttpHeaderNameAndValueSeparator = ":"

  case class InternalNativeFailure(errorCode: Long, errorDescription: String) extends RuntimeException(s"$errorCode: $errorDescription")

  case class InfiniteRedirect(visited: List[String]) extends RuntimeException(s"Infinte redirect detected: $visited")

  /**
   * Initializes libcurl` internal state by calling `curl_global_init` underneath.
   * This method is _NOT_ thread safe and it's meant to be called at the start of the program.
   */
  def globalInit(): Try[Unit] =
    native.Zone { implicit z =>
      val errorCode = nativebinding.httpsimple.global_init()
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
      nativebinding.httpsimple.global_cleanup()
    }

  def apply(request: HttpRequest): Try[HttpResponse] =
    doHttp(request.requestMethod, request.requestUrl, request.requestHeaders.headers, request.requestBody, request.requestFollowRedirects, Nil)

  private def doHttp(
    method: String,
    url: String,
    headers: Map[String, String],
    requestBody: Option[String],
    followRedirects: Boolean,
    visitedUrls: List[String]): Try[HttpResponse] =
    native.Zone { implicit z =>
      val http_response_struct = nativebinding.httpsimple.do_http(
        native.toCString(method),
        native.toCString(url),
        native.toCString(httpHeadersToDelimitedString(headers)),
        native.toCString(requestBody.getOrElse("")))

      val errorCode = nativebinding.httpsimple.get_error_code(http_response_struct).cast[Long]
      val result =
        if (errorCode == 0) {
          val httpStatus = nativebinding.httpsimple.get_http_status(http_response_struct).cast[Long]
          val rawHttpResponse = native.fromCString(nativebinding.httpsimple.get_raw_http_response(http_response_struct))
          val (headers, body) = rawHttpResponseToHttpHeadersAndBody(rawHttpResponse)
          val hs = HttpHeaders(headers)

          if (followRedirects && hs.contains("Location")) {
            val location = hs("Location")

            if (visitedUrls.contains(location))
              Failure(InfiniteRedirect(visitedUrls))
            else
              doHttp("GET", location, Map.empty, None, true, location :: visitedUrls)
          } else {
            Success(HttpResponse(httpStatus, hs, body))
          }
        } else {
          Failure(InternalNativeFailure(errorCode, errorMessageFromCode(errorCode)))
        }

      // Always cleanup to free the memory
      nativebinding.httpsimple.cleanup_http_response(http_response_struct)

      result
    }

  private def httpHeadersToDelimitedString(headers: Map[String, String]): String =
    headers
      .map {
        case (headerName, headerValue) => s"$headerName$HttpHeaderNameAndValueSeparator $headerValue"
      }
      .mkString(CRLF)

  private def rawHttpResponseToHttpHeadersAndBody(input: String): (Map[String, String], Option[String]) = {
    def splitBySeparator(v: String, separator: String): (String, String) = {
      val lineBreakIndex = v.indexOf(separator)
      val (l, r) = v.splitAt(lineBreakIndex)
      l -> r.substring(separator.length)
    }

    Option(input) match {
      case v @ Some(rawResponse) =>
        val (headerText, responseBody) = splitBySeparator(rawResponse, HttpHeaderAndBodyPartsSeparator)

        // Exclude the first line which is the HTTP status line
        val headers = headerText.split(CRLF).tail.foldLeft(Map.empty[String, String]) { (v, l) =>
          val (headerName, headerValue) = splitBySeparator(l, HttpHeaderNameAndValueSeparator)
          v.updated(headerName, headerValue.trim)
        }

        headers -> Option(responseBody)
      case _ =>
        Map.empty[String, String] -> Option.empty[String]
    }
  }

  private def errorMessageFromCode(errorCode: Long): String =
    if (errorCode == 1)
      "failure to `malloc` when initializing `raw_response`"
    else if (errorCode == 2)
      "failure to `realloc` when writing HTTP response body into to `raw_response`"
    else if (errorCode == 77)
      "failure to invoke `curl_easy_perform`."
    else
      "Unknown failure invoking native code"

}
