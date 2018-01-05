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

package libhttpsimple.nativebinding

import libhttpsimple.curl._
import scala.scalanative.native
import scala.scalanative.native._

object httpsimple {

  case class HttpResponse(error: Long, status: Long, header: Option[String], body: Option[String])

  def global_init(): CInt = {
    val res = curl.global_init(curl.CURLGlobals.CURL_GLOBAL_DEFAULT)
    if (res == curl.CURLcode.CURLE_OK) 0
    else {
      System.err.println("curl_global_init() failed: " + curl.easy_strerror(res).toString)
      -1
    }
  }

  def global_cleanup(): Unit = {
    curl.global_cleanup()
  }

  def do_http(validate_tls: CLong, http_method: String,
              url: String, request_headers: Seq[String],
              request_body: String, tls_cacerts_path: String,
              ssl_cert: String, ssl_key: String)(implicit z: Zone): HttpResponse = {

    var result = HttpResponse(-1L, 0L, None, None)
    val req = curl.easy_init()

    // For debugging
    //curl.easy_setopt(req, curl.CURLoption.CURLOPT_VERBOSE, 1L)

    // Set up options
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_URL, toCString(url))
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_CUSTOMREQUEST, toCString(http_method))
    if (validate_tls == 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSL_VERIFYPEER, 0L)
    if (tls_cacerts_path.length > 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_CAINFO, toCString(tls_cacerts_path))
    if (ssl_cert.length > 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSLCERT, toCString(ssl_cert))
    if (ssl_key.length > 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSLKEY, toCString(ssl_key))
    if (request_body.length > 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_POSTFIELDS, toCString(request_body))

    // Set up http headers
    if (request_headers.length > 0) {
      if (request_headers.length != 1) {
        System.out.println("Too many request headers, at most one is supported now")
      }

      val list = stackalloc[curl.curl_slist]
      !list._1 = toCString(request_headers.head)
      !list._2 = 0.cast[Ptr[Byte]]

      curl.easy_setopt(req, curl.CURLoption.CURLOPT_HTTPHEADER, list)
    }

    val body = new StringBuilder()
    val header = new StringBuilder()

    def writefunc(ptr: Ptr[Byte], size: CSize, nmemb: CSize, builder: StringBuilder): CSize = {
      val realsize: CSize = size * nmemb
      var i = 0
      while (i < realsize) {
        builder.append(ptr(i).toChar)
        i += 1
      }
      realsize
    }

    val writecallback = CFunctionPtr.fromFunction4(writefunc)
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_WRITEFUNCTION, writecallback)
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_HEADERFUNCTION, writecallback)
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_WRITEDATA, body)
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_HEADERDATA, header)

    def buildString(b: StringBuilder): Option[String] = {
      if (b.length > 0) Some(b.toString)
      else None
    }

    // Perform request
    val res = curl.easy_perform(req)
    if (res == curl.CURLcode.CURLE_OK) {
      // Zone here shouldn't be needed but it functions as a workaround for scala-native bug.
      // Without zone here, compiler crashes:
      // [error] (cli/compile:nativeOptimizeNIR) java.util.NoSuchElementException: key not found: Local(167)
      Zone { implicit z =>
        val response_code = stackalloc[CLong]
        curl.easy_getinfo(req, curl.CURLINFO.RESPONSE_CODE, response_code)
        result = HttpResponse(0L, !response_code, buildString(header), buildString(body))
      }
    }

    curl.easy_cleanup(req)

    result
  }

  def error_message(error_code: Long): String = {
    fromCString(curl.easy_strerror(new curl.CURLcode(error_code.toInt)))
  }
}
