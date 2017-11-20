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

package libhttpsimple

import scala.annotation.tailrec

object Base64Encoder {
  private val charSet: Seq[Char] =
    ('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') ++ Seq('+', '/')
  private val blockLength: Int = 3
  private val padding: String = "="

  /**
   * Inspired by https://en.wikibooks.org/wiki/Algorithm_Implementation/Miscellaneous/Base64#Java
   */
  def apply(in: String): String = {
    val charsToPad =
      if (in.length % blockLength == 0)
        0
      else
        blockLength - (in.length % blockLength)

    val data = in + ("\u0000" * charsToPad)
    val rightPad = padding * charsToPad

    @tailrec
    def encodeBlock(blockPosition: Int, result: Seq[Char]): Seq[Char] =
      if (blockPosition >= data.length)
        result
      else {
        val n = (data.charAt(blockPosition) << 16) + (data.charAt(blockPosition + 1) << 8) + data.charAt(blockPosition + 2)
        val encoded: Seq[Char] = Seq(
          charSet((n >> 18) & 63),
          charSet((n >> 12) & 63),
          charSet((n >> 6) & 63),
          charSet(n & 63))
        encodeBlock(blockPosition = blockPosition + blockLength, result ++ encoded)
      }

    val chars = encodeBlock(blockPosition = 0, Seq.empty)
    chars.mkString.substring(0, chars.length - rightPad.length) + rightPad
  }
}