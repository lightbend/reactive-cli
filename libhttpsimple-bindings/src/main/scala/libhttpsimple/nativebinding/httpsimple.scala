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

import scala.scalanative.native
import scala.scalanative.native._

@native.link("httpsimple")
@native.extern
object httpsimple {
  type http_response = native.extern

  def global_init(): CInt = native.extern
  def global_cleanup(): Unit = native.extern
  def do_http(validate_tls: CLong, tls_cacerts_path: CString, http_method: CString, url: CString, request_headers_raw: CString, auth_type: CString, auth_value: CString, request_body: CString): Ptr[http_response] = native.extern
  def get_error_code(http_response: Ptr[http_response]): CLong = native.extern
  def get_error_message(http_response: Ptr[http_response]): CString = native.extern
  def get_http_status(http_response: Ptr[http_response]): CLong = native.extern
  def get_raw_http_response(http_response: Ptr[http_response]): CString = native.extern
  def cleanup_http_response(http_response: Ptr[http_response]): Unit = native.extern
}
