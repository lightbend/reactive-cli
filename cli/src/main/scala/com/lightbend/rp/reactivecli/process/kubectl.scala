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

import scala.collection.immutable.Seq
import slogging._

object kubectl extends LazyLogging {
  lazy val apiVersions: Seq[String] = {
    val (code, output) = exec("kubectl", "api-versions")

    if (code == 0) {
      output
        .replaceAllLiterally("\r\n", "\n")
        .split('\n')
        .map(_.trim)
        .filter(_.nonEmpty)
        .toVector
    } else {
      logger.debug(s"kubectl version exited with $code")

      Seq.empty
    }
  }

  def findApi(preferred: String, other: String*): String =
    (preferred +: other)
      .find(apiVersions.contains)
      .getOrElse(preferred)
}
