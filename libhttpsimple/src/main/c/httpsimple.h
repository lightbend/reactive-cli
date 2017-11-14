/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Lightbend, Inc.
 */

#ifndef httpsimple_h__
#define httpsimple_h__

struct http_response {
  /*
  has_error: error code which indicates internal error within the httpsimple.
  IMPORTANT: This error code has nothing to do with HTTP response.
  - `0`: all ok, no error.
  - `1`: failure to `malloc` when initializing `raw_response`.
  - `2`: failure to `realloc` when writing HTTP response body into to `raw_response`.
  - `77`: failure to invoke `curl_easy_perform`.
  */
  long has_error;
  char *error_message;
  long http_status;
  char *raw_response;
  size_t len;
};

extern int global_init();

extern void global_cleanup();

extern struct http_response *do_http(long validate_tls, char *tls_cacerts_path, char *http_method, char *url, char *request_headers_raw, char *auth_type, char *auth_value, char *request_body);

extern long get_error_code(struct http_response *s);

extern char *get_error_message(struct http_response *s);

extern long get_http_status(struct http_response *s);

extern char *get_raw_http_response(struct http_response *s);

extern void cleanup_http_response(struct http_response *s);

#endif // httpsimple_h__
