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

  def do_http(validate_tls: CLong, http_method: CString,
              url: CString, request_headers_raw: CString,
              request_body: CString, tls_cacerts_path: CString,
              ssl_cert: CString, ssl_key: CString): HttpResponse = {

    var result = HttpResponse(-100, 404, None)
    val req = curl.easy_init()
    if(req != 0) {
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_URL, url)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_CUSTOMREQUEST, http_method)
      curl.easy_setopt(req, curl.CURLoption.CURLOPT_HEADER, 1L)
      if(validate_tls == 0)
        curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSL_VERIFYPEER, 0L)
      if(tls_cacerts_path != 0 && fromCString(tls_cacerts_path).length > 0)
        curl.easy_setopt(req, curl.CURLoption.CURLOPT_CAINFO, tls_cacerts_path)
      if(ssl_cert != 0 && fromCString(ssl_cert).length > 0)
        curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSLCERT, ssl_cert)
      if(ssl_key != 0 && fromCString(ssl_key).length > 0)
        curl.easy_setopt(req, curl.CURLoption.CURLOPT_SSLKEY, ssl_key)

      if(request_headers_raw != 0 && fromCString(request_headers_raw).length > 0) {
        // TODO(mitkus): Request headers
        System.out.println("Request headers: " + fromCString(request_headers_raw))
      }

      if(request_body != 0 && fromCString(request_body).length > 0)
        curl.easy_setopt(req, curl.CURLoption.CURLOPT_POSTFIELDS, request_body)

      def writefunc(ptr: Ptr[Byte], size: CSize, nmemb: CSize, userdata: CString): Unit = {
        // TODO(mitkus): Handle response
      }

      curl.easy_setopt(req, curl.CURLoption.CURLOPT_WRITEFUNCTION, CFunctionPtr.fromFunction4(writefunc))
      //curl.easy_setopt(req, curl.CURLoption.CURLOPT_WRITEDATA, ...)

      val res = curl.easy_perform(req)

      try {
        if (res == curl.CURLcode.CURLE_OK) {
          Zone { implicit z => {
            val response_code = alloc[CLong]
            curl.easy_getinfo(req, curl.CURLINFO.RESPONSE_CODE, response_code)
            result = HttpResponse(0, !response_code, Some("Fake http response!"))
          }}
        }
      }
      finally {
        curl.easy_cleanup(req)
      }
    }

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
