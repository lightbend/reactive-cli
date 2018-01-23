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

import utest._

// FIXME scala native doesn't seem to like tests in two different projects in the
// FIXME same build so it throws a [error] (cli/nativetest:nativeLinkNIR) java.nio.file.FileSystemAlreadyExistsException

object HttpTest extends TestSuite {
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

    "Parse authentication header" - {
      assert(parseAuthHeader("") == Some(Map()))
      assert(parseAuthHeader("key=\"val1\"") == Some(Map("key" -> "val1")))
      assert(parseAuthHeader("a=\"val1\",b = \"val2\"") == Some(Map("a" -> "val1", "b " -> "val2")))
      assert(parseAuthHeader(" a=\"\",b = \"val2\"") == Some(Map("a" -> "", "b " -> "val2")))
      assert(parseAuthHeader(" p  a=\"1\",b= \"2\"") == Some(Map("p  a" -> "1", "b" -> "2")))

      val data = """Bearer realm="https://auth.docker.io/token",service="registry.docker.io",scope="repository:dockercloud/hello-world:pull""""
      assert(parseAuthHeader(data) == Some(Map(
        "Bearer realm" -> "https://auth.docker.io/token",
        "service" -> "registry.docker.io",
        "scope" -> "repository:dockercloud/hello-world:pull")))

      assert(parseAuthHeader("key =") == None)
      assert(parseAuthHeader("key = value") == None)
      assert(parseAuthHeader(",") == None)
      assert(parseAuthHeader("a=\"val1\"b = \"val2\"") == None)
    }
  }
}
