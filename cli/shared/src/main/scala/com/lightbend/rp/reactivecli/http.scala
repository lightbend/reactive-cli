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

package com.lightbend.rp.reactivecli

import fastparse.all._

package object http {
  def encodeURI(uri: String): String = Platform.encodeURI(uri)

  def parseAuthHeader(auth: String): Option[Map[String, String]] = {
    val ws = P(CharIn(" \t").rep(1))
    val letters = P(CharIn('a' to 'z', 'A' to 'Z') ~ CharsWhile(_ != '=', min = 0))
    val value = P("\"" ~ CharsWhile(_ != '\"', min = 0).! ~ "\"")
    val keyval = P(ws.? ~ letters.! ~ "=" ~ ws.? ~ value)
    val parser = P(Start ~ keyval.rep(sep = ",") ~ End)

    parser.parse(auth) match {
      case Parsed.Success(seq, _) => Some(seq.toMap)
      case Parsed.Failure(_, _, _) => None
    }
  }
}
