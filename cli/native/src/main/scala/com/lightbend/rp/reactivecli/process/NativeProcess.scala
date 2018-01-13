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

import com.lightbend.rp.reactivecli.files._
import scala.concurrent.Future
import scalanative.native._
import slogging._

object NativeProcess extends LazyLogging {
  def exec(args: String*): Future[(Int, String)] =
    withTempFile { outputFile =>
      Zone { implicit z =>
        val code = stdlib.system(toCString(s"${command(args)} > $outputFile 2>&1"))
        val output = readFile(outputFile)

        Future.successful(code -> output)
      }
    }

  /**
   * Prepares a sequence of arguments to be passed to system(). We assume a POSIX target for now, which means
   * the command will be processed by `sh` per POSIX specification.
   *
   * This means that we can simply enclose each argument in a single quote. However, if a single quote occurs in
   * an argument, we special case that by enclosing it in double quotes.
   */
  private[NativeProcess] def command(args: Seq[String]): String = {
    def escape(s: String): String =
      "'" + s.replaceAllLiterally("'", "'\"'\"'") + "'"

    args
      .map(escape)
      .mkString(" ")
  }
}
