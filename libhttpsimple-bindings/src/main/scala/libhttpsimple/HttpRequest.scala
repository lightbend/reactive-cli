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

case class HttpRequest(
  requestUrl: String,
  requestMethod: String = "GET",
  requestHeaders: HttpHeaders = HttpHeaders(Map.empty),
  requestBody: Option[String] = None,
  requestFollowRedirects: Boolean = false) {

  def disableFollowRedirects: HttpRequest = copy(requestFollowRedirects = false)

  def enableFollowRedirects: HttpRequest = copy(requestFollowRedirects = true)

  def get: HttpRequest = copy(requestMethod = "GET")

  def headers(headers: HttpHeaders): HttpRequest = copy(requestHeaders = headers)

  def noContent: HttpRequest = copy(requestBody = None)

  def post: HttpRequest = copy(requestMethod = "POST")

  def url(url: String): HttpRequest = copy(requestUrl = url)

  def withContent(body: String): HttpRequest = copy(requestBody = Some(body))

  def withHeader(name: String, value: String): HttpRequest = copy(requestHeaders = requestHeaders.updated(name, value))

  def withoutHeader(name: String): HttpRequest = copy(requestHeaders = requestHeaders.remove(name))
}
