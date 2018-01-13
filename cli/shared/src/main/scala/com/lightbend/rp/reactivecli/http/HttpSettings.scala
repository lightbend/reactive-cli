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

/**
 * Common settings for [[Http]].
 *
 * @param followRedirect If true, follow redirect up to the number of hops specified by [[maxRedirects]].
 *                       Else, doesn't follow redirect, i.e. returns the response with `Location` header.
 * @param tlsValidationEnabled If true, instructs the underlying `libcurl` to perform TLS validation.
 *                             Else, `libcurl` will set `CURLOPT_SSL_VERIFYPEER` to `0`.
 * @param tlsCacertsPath Paths to CA certs used for TLS validation.
 *                       Optional. If specified, this will be supplied to `libcurl` via `CURLOPT_CAINFO` option.
 * @param maxRedirects The maximum number of redirects allowed when attempting HTTP request.
 *                     This setting is in place to prevent infinite redirect loop.
 */
case class HttpSettings(
  followRedirect: Boolean = true,
  tlsValidationEnabled: Boolean = true,
  tlsCacertsPath: Option[String] = None,
  tlsCertPath: Option[String] = None,
  tlsKeyPath: Option[String] = None,
  maxRedirects: Int = HttpSettings.DefaultMaxRedirects)

object HttpSettings {
  val DefaultMaxRedirects = 5

  val default = HttpSettings()
}