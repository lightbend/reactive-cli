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

object Base64Encoder {
  private val charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

  /**
   * Inspired by https://en.wikibooks.org/wiki/Algorithm_Implementation/Miscellaneous/Base64#Java
   */
  def apply(in: String): String = {
    val charsToPad =
      if (in.length % 3 == 0)
        0
      else
        3 - (in.length % 3)

    val data = in + ("\u0000" * charsToPad)
    val rightPad = "=" * charsToPad
    val builder = new StringBuilder

    var i = 0

    while (i < data.length) {
      val n = (data.charAt(i) << 16) + (data.charAt(i + 1) << 8) + data.charAt(i + 2)

      builder.append(charSet.charAt((n >> 18) & 63))
      builder.append(charSet.charAt((n >> 12) & 63))
      builder.append(charSet.charAt((n >> 6) & 63))
      builder.append(charSet.charAt(n & 63))

      i += 3
    }

    builder.substring(0, builder.length - rightPad.length) + rightPad
  }
}