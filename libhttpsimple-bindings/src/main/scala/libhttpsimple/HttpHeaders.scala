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

case class HttpHeaders(headers: Map[String, String]) {
  private val lowerCaseHeaders =
    headers
      .keys
      .map(h => toLowerCase(h) -> h)
      .toMap

  def apply(name: String): String = headers(lowerCaseHeaders(toLowerCase(name)))

  def contains(name: String): Boolean =
    lowerCaseHeaders.contains(toLowerCase(name))

  def header(name: String): Option[String] =
    for {
      h <- lowerCaseHeaders.get(toLowerCase(name))
      v <- headers.get(h)
    } yield v

  def updated(name: String, value: String): HttpHeaders =
    copy(headers = headers.updated(headerName(name), value))

  def remove(name: String): HttpHeaders =
    copy(headers = headers - headerName(name))

  private def headerName(name: String): String =
    lowerCaseHeaders.getOrElse(toLowerCase(name), name)

  private def toLowerCase(s: String): String = {
    // FIXME replace with inlined .toLowerCase once https://github.com/scala-native/scala-native/pull/1037 merged

    s"${s.toLowerCase}"
  }
}
