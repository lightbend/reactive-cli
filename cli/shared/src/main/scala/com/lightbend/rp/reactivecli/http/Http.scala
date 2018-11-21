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

import com.lightbend.rp.reactivecli.Platform
import com.lightbend.rp.reactivecli.concurrent._
import slogging.LazyLogging

import scala.concurrent.Future

object Http extends LazyLogging {
  type HttpExchange = HttpRequest => Future[HttpResponse]

  case class InfiniteRedirect(visited: List[String]) extends RuntimeException(s"Infinte redirect detected: $visited")

  def http(implicit settings: HttpSettings): HttpExchange = apply

  def apply(request: HttpRequest)(implicit settings: HttpSettings): Future[HttpResponse] = doRequest(request, Nil)

  private def doRequest(
                         request: HttpRequest,
                         visitedUrls: List[String])(implicit settings: HttpSettings): Future[HttpResponse] = {
    val isFollowRedirect = request.requestFollowRedirects.getOrElse(settings.followRedirect)

    Platform
      .httpRequest(request)
      .flatMap { response =>
        if (isFollowRedirect &&
          response.statusCode >= 300 &&
          response.statusCode <= 399 &&
          response.headers.contains("Location")) {

          val location = response.headers("Location")

          if (visitedUrls.contains(location) || visitedUrls.length >= settings.maxRedirects) {
            logger.debug("No more redirects allowed")
            Future.failed(InfiniteRedirect(visitedUrls))
          } else {
            doRequest(
              request.copy(location), location :: visitedUrls)
          }
        } else {
          Future.successful(response)
        }
      }
  }
}
