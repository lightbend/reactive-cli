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

import utest._

// FIXME scala native doesn't seem to like tests in two different projects in the
// FIXME same build so it throws a [error] (cli/nativetest:nativeLinkNIR) java.nio.file.FileSystemAlreadyExistsException

object Base64Test extends TestSuite {
  val tests = this{
    "Encode Strings to base64" - {
      val tests = Map(
        "abc" -> "YWJj",
        "test" -> "dGVzdA==",
        "a" -> "YQ==",
        "ab" -> "YWI=",
        "abc" -> "YWJj",
        "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXpBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWjAxMjM0" -> "WVdKalpHVm1aMmhwYW10c2JXNXZjSEZ5YzNSMWRuZDRlWHBCUWtORVJVWkhTRWxLUzB4TlRrOVFVVkpUVkZWV1YxaFpXakF4TWpNMA==")

      tests.foreach {
        case (in, out) =>
          val encoded = Base64Encoder(in)

          assert(encoded == out)
      }
    }
  }
}
