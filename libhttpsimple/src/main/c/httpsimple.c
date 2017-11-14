/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Lightbend, Inc.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <curl/curl.h>
#include "httpsimple.h"

int global_init() {
  CURLcode res_curl_code;
  res_curl_code = curl_global_init(CURL_GLOBAL_DEFAULT);

  if (res_curl_code == CURLE_OK) {
    return 0;
  } else {
    fprintf(stderr, "curl_global_init() failed: %s\n", curl_easy_strerror(res_curl_code));
    return -1;
  }
}

void global_cleanup() {
  curl_global_cleanup();
}

void init_http_response(struct http_response *s) {
  s->has_error = 0;
  s->http_status = 0;
  s->len = 0;
  s->raw_response = malloc(s->len+1);
  if (s->raw_response == NULL) {
    s->has_error = 1;
  }
  s->raw_response[0] = '\0';
}

size_t writefunc(void *ptr, size_t size, size_t nmemb, struct http_response *s)
{
  size_t new_len = s->len + size * nmemb;
  s->raw_response = realloc(s->raw_response, new_len + 1);
  if (s->raw_response == NULL) {
    s->has_error = 2;
  }
  memcpy(s->raw_response + s->len, ptr, size * nmemb);
  s->raw_response[new_len] = '\0';
  s->len = new_len;

  return size * nmemb;
}

struct http_response *do_http(long validate_tls, char *tls_cacerts_path, char *http_method, char *url, char *request_headers_raw, char *auth_type, char *auth_value, char *request_body) {
  CURL *curl;
  CURLcode res_curl_code;
  struct http_response *s = malloc(sizeof(struct http_response));

  init_http_response(s);

  if (s->has_error == 0) {
    curl = curl_easy_init();
    if(curl) {
      curl_easy_setopt(curl, CURLOPT_URL, url);
      curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, http_method);
      curl_easy_setopt(curl, CURLOPT_HEADER, 1L); // Return header as part of the response text

      if (validate_tls == 0L) {
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
      }

      if (tls_cacerts_path && strlen(tls_cacerts_path) > 0) {
        curl_easy_setopt(curl, CURLOPT_CAINFO, tls_cacerts_path);
      }

      if (auth_type && strlen(auth_type) > 0 &&
            auth_value && strlen(auth_value) > 0) {
        if (strcmp("basic", auth_type) == 0) {
            curl_easy_setopt(curl, CURLOPT_USERPWD, auth_value);
        } else if (strcmp(auth_type, "bearer")) {
            curl_easy_setopt(curl, CURLOPT_XOAUTH2_BEARER, auth_value);
        }
      }

      // Append request headers if defined
      if (request_headers_raw && strlen(request_headers_raw) > 0) {
        struct curl_slist *chunk = NULL;

        // Need to duplicate the string, else will result in segfault
        char *request_headers_raw_dup = strdup(request_headers_raw);
        // Split the first token
        char *request_header_line = strtok(request_headers_raw_dup, "\r\n");

        // Append the first request header
        chunk = curl_slist_append(chunk, request_header_line);

        // Iterate the remaining request header, appending while iterating
        while (request_header_line != NULL) {
          chunk = curl_slist_append(chunk, request_header_line);
          request_header_line = strtok(NULL, "\r\n");
        }
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, chunk);
      }

      // Append request body if defined
      if (request_body && strlen(request_body) > 0) {
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, request_body);
      }

      curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writefunc);
      curl_easy_setopt(curl, CURLOPT_WRITEDATA, s);

      /* Perform the request, res will get the return code */
      res_curl_code = curl_easy_perform(curl);
      /* Check for errors */
      if (res_curl_code == CURLE_OK && s->has_error == 0) {
        long http_response_code;
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_response_code);
        s->http_status = http_response_code;
      } else {
        s->has_error = 77;
        fprintf(stderr, "curl_easy_perform() failed: %s\n", curl_easy_strerror(res_curl_code));
      }

      // Always cleanup the `curl` handle
      curl_easy_cleanup(curl);
    }
  }

  return s;
}

long get_error_code(struct http_response *s) {
  return s->has_error;
}

char *get_error_message(struct http_response *s) {
  return s->error_message;
}

long get_http_status(struct http_response *s) {
  return s->http_status;
}

char *get_raw_http_response(struct http_response *s) {
  return s->raw_response;
}

void cleanup_http_response(struct http_response *s) {
  free(s->raw_response);
  free(s);
}
