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

package com.lightbend.rp.reactivecli.docker
import scala.collection.immutable.Seq
import utest._

object DockerCredentialsTest extends TestSuite {
  val tests = this{
    "Parse Credentials" - {
      val result = DockerCredentials.decodeCreds(
        """|registry = lightbend-docker-registry.bintray.io
           |username=hello
           |password =      there
           |
           |reg=what
           |
           |registry= registry.hub.docker.com
           |password = bar
           |username    = foo
           |
           |registry= 1.hub.docker.com
           |password = what
           |registry = 2.hub.docker.com
           |username = ok
           |""".stripMargin)

      val expected = Seq(
        DockerCredentials("lightbend-docker-registry.bintray.io", "hello", "there", ""),
        DockerCredentials("registry.hub.docker.com", "foo", "bar", ""),
        DockerCredentials("2.hub.docker.com", "ok", "what", ""))

      assert(result == expected)
    }
    "Parse Docker Config" - {
      val resultEmpty = DockerCredentials.decodeConfig("""{"auths": {} }""")
      val result = DockerCredentials.decodeConfig(
        """
          |{
          |   "auths": {
          |       "https://index.docker.io/v1/": {
          |         "auth": "0123abcdef="
          |       },
          |       "lightbend-docker-registry.bintray.io": {
          |         "auth": "xzyw="
          |       }
          |   }
          |}
        """.stripMargin)

      val expected = Seq(
        DockerCredentials("https://index.docker.io/v1/", "", "", "0123abcdef="),
        DockerCredentials("lightbend-docker-registry.bintray.io", "", "", "xzyw="))

      assert(resultEmpty == Seq.empty)
      assert(result == expected)
    }
  }
}
