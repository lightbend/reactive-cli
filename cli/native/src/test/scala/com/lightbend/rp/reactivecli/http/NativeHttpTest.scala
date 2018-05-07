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

import scala.collection.immutable.Map
import utest._

object NativeHttpTest extends TestSuite {
  val tests = this {
    "Parse HTTP headers" - {
      // No header field value
      assert(NativeHttp.parseHeaders(Some("HTTP/1.1 200 OK\r\nAccept:")) == Map(
        "Accept" -> ""
      ))

      // Real-world punctuation
      assert(NativeHttp.parseHeaders(Some(
        """HTTP/1.1 200 OK
          |Date: Mon, 07 May 2018 08:43:13 GMT
          |Expires: -1
          |Cache-Control: private, max-age=0
          |Content-Type: text/html; charset=ISO-8859-1""".stripMargin.replaceAll("\n", "\r\n"))) == Map(
        "Date" -> "Mon, 07 May 2018 08:43:13 GMT",
        "Expires" -> "-1",
        "Cache-Control" -> "private, max-age=0",
        "Content-Type" -> "text/html; charset=ISO-8859-1"
      ))

      // Multiline field values
      assert(NativeHttp.parseHeaders(Some(
        """HTTP/1.1 200 OK
          |Date: Mon, 07 May 2018
          | 08:43:13 GMT
          |Expires: -1
          |Cache-Control: private,
          |               max-age=0
          |Content-Type: text/html; charset=ISO-8859-1""".stripMargin.replaceAll("\n", "\r\n"))) == Map(
        "Date" -> "Mon, 07 May 2018 08:43:13 GMT",
        "Expires" -> "-1",
        "Cache-Control" -> "private, max-age=0",
        "Content-Type" -> "text/html; charset=ISO-8859-1"
      ))
    }
  }
}