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
  case class HttpResponse(error: Int, status: Long, body: Option[String])

  def global_init(): CInt = {
    // curl_easy_init calls global init implicitly, no need to do anything here
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

    var result = HttpResponse(0, 0, None)
    val req = curl.easy_init()

    // For debugging
    //curl.easy_setopt(req, curl.CURLoption.CURLOPT_VERBOSE, 1L)

    // Set up options
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_URL, toCString(url))
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_CUSTOMREQUEST, toCString(http_method))
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_HEADER, 1L)
    if(validate_tls == 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSL_VERIFYPEER, 0L)
    if(tls_cacerts_path.length > 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_CAINFO, toCString(tls_cacerts_path))
    if(ssl_cert.length > 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSLCERT, toCString(ssl_cert))
    if(ssl_key.length > 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSLKEY, toCString(ssl_key))
    if(request_body.length > 0)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_POSTFIELDS, toCString(request_body))

    // Set up http headers
    if(request_headers.length > 0) {
      if(request_headers.length != 1) {
        System.out.println("Too many request headers, at most one is supported now")
      }

      val list = stackalloc[curl.curl_slist]
      !list._1 = toCString(request_headers.head)
      !list._2 = 0.cast[Ptr[Byte]]

      curl.easy_setopt(req, curl.CURLoption.CURLOPT_HTTPHEADER, list)
    }

    // Set up text buffer and content write callback
    val response_buffer_size = 32 * 1024
    val data = alloc[CChar](response_buffer_size)
    def writefunc(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: CString): CSize = {
      // Scala native cannot retrieve function pointer of a closure which
      // captures environment, so repeat our constant here
      val response_buffer_size = 32 * 1024
      val realsize : CSize = size * nmemb
      if(realsize >= response_buffer_size) {
        System.err.println(s"libcurl writefunc buffer too small - need $realsize, have $response_buffer_size")
        0
      }
      else {
        var i = 0
        while(i < realsize) {
          data(i) = ptr(i).cast[CChar]

          i += 1
        }
        data(i) = 0.toByte.cast[CChar]
        realsize
      }
    }

    curl.easy_setopt(req, curl.CURLoption.CURLOPT_WRITEFUNCTION, CFunctionPtr.fromFunction4(writefunc))
    curl.easy_setopt(req, curl.CURLoption.CURLOPT_WRITEDATA, data)

    // Perform request
    val res = curl.easy_perform(req)
    if (res == curl.CURLcode.CURLE_OK) {
      Zone { implicit z =>
        val response_code = stackalloc[CLong]
        curl.easy_getinfo(req, curl.CURLINFO.RESPONSE_CODE, response_code)
        result = HttpResponse(0, !response_code, Some(fromCString(data)))
      }
    }
    else {
      System.out.println(s"HTTP failed: $res")
    }

    curl.easy_cleanup(req)

    result
  }

  def get_error_code(http_response: HttpResponse): CLong = {
    http_response match {
      case HttpResponse(error, _, _) => error
    }
  }

  def get_error_message(http_response: HttpResponse): CString = {
    http_response match {
      case HttpResponse(error, _, _) => curl.easy_strerror(new curl.CURLcode(error))
    }
  }

  def get_http_status(http_response: HttpResponse): CLong = {
    http_response match {
      case HttpResponse(_, status, _) => status
    }
  }

  def get_raw_http_response(http_response: HttpResponse): String = {
    http_response match {
      case HttpResponse(_, _, Some(response)) => response
    }
  }

  def cleanup_http_response(http_response: HttpResponse): Unit = {}
}
