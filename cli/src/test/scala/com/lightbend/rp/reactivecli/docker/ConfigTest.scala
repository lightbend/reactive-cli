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

import argonaut._
import utest._

import Argonaut._
case class Cfg(
  Hostname: Option[String] = None,
  ExposedPorts: Option[Map[String, String]] = None,
  Cmd: Option[Vector[String]] = None,
  Image: Option[String] = None,
  User: Option[String] = None,
  Labels: Option[Map[String, String]] = None)
object ConfigTest extends TestSuite {
  val tests = this{
    "Decode JSON" - {
      assert(
        """{}""""
          .decodeOption[Config]
          .isEmpty)

      assert(
        """{ "config": {} }"""
          .decodeOption[Config]
          .contains(Config(Config.Cfg())))

      assert(
        """|{
           |  "config": {
           |    "Labels": {
           |      "test.one": "test one!",
           |      "test.two": "test two!"
           |    },
           |    "Hostname": "test",
           |    "ExposedPorts": { "80/tcp": {}},
           |    "Cmd": ["/bin", "/sh"],
           |    "Image": "abc123",
           |    "User": "root"
           |  }
           |}"""
          .stripMargin
          .decodeOption[Config]
          .contains(
            Config(
              Config.Cfg(
                Labels = Some(Map("test.one" -> "test one!", "test.two" -> "test two!")),
                Hostname = Some("test"),
                ExposedPorts = Some(Map("80/tcp" -> Map.empty)),
                Cmd = Some(Vector("/bin", "/sh")),
                Image = Some("abc123"),
                User = Some("root")))))
    }
  }
}
