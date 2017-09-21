/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Lightbend, Inc.
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
  def do_http(http_method: CString, url: CString, request_headers_raw: CString, request_body: CString): Ptr[http_response] = native.extern
  def get_error_code(http_response: Ptr[http_response]): CLong = native.extern
  def get_error_message(http_response: Ptr[http_response]): CString = native.extern
  def get_http_status(http_response: Ptr[http_response]): CLong = native.extern
  def get_raw_http_response(http_response: Ptr[http_response]): CString = native.extern
  def cleanup_http_response(http_response: Ptr[http_response]): Unit = native.extern
}
