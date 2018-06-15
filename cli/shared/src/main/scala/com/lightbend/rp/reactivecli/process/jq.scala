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

package com.lightbend.rp.reactivecli.process

import argonaut._
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.json.JsonTransformExpression
import scala.concurrent.Future
import slogging._

import Argonaut._

object jq extends LazyLogging {
  lazy val available: Future[Boolean] =
    exec("jq", "--version").map(_._1 == 0)

  def apply(filter: JsonTransformExpression, input: String): Future[String] =
    withTempFile { inputFile =>
      writeFile(inputFile, input)

      for {
        (code, output) <- exec("jq", "-M", filter.value, inputFile.toString)
      } yield {
        if (code != 0) {
          logger.error(output)

          throw new RuntimeException(s"jq exited with $code but 0 was expected")
        }

        output
      }
    }

  def jsonTransform(json: Json, expr: JsonTransformExpression): Future[Json] =
    apply(expr, json.nospaces)
        .map(
          _
            .parse
            .fold(
                error => throw new RuntimeException(s"Unable to parse output from jq: $error"),
                identity))
}
