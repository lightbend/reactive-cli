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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import slogging._

object jq extends LazyLogging {
  lazy val available: Boolean =
    exec("jq", "--version")._1 == 0

  def apply(filter: String, input: String): String =
    withTempFile { inputFile =>
      Files.write(inputFile, input.getBytes(StandardCharsets.UTF_8))

      val (code, output) = exec("jq", "-M", filter, inputFile.toString)

      if (code != 0) {
        logger.error(output)

        throw new RuntimeException(s"jq exited with $code but 0 was expected")
      }

      output
    }
}
