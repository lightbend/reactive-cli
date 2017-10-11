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

import argonaut.Argonaut._
import argonaut._
import utest._

object ManifestTest extends TestSuite {
  val tests = this{
    "Decode JSON" - {
      "empty" - assert(
        """{}""""
          .decodeOption[Manifest]
          .isEmpty
      )

      "no layers" - assert(
        """|{
           |  "schemaVersion": 2,
           |  "mediaType": "test",
           |  "config": { "mediaType": "test", "size": 8192, "digest": "abc123" },
           |  "layers": []
           |}"""
          .stripMargin
          .decodeOption[Manifest]
          .contains(Manifest(2, "test", Manifest.Layer("test", 8192, "abc123"), Vector.empty))
      )

      assert(
        """|{
           |  "schemaVersion": 2,
           |  "mediaType": "test2",
           |  "config": { "mediaType": "test2", "size": 4096, "digest": "xyz456" },
           |  "layers": [
           |    { "mediaType": "test3", "size": 1, "digest": "oh" },
           |    { "mediaType": "test4", "size": 2, "digest": "rly" }
           |  ]
           |}"""
          .stripMargin
          .decodeOption[Manifest]
          .contains(
            Manifest(
              2,
              "test2",
              Manifest.Layer("test2", 4096, "xyz456"),
              Vector(Manifest.Layer("test3", 1, "oh"), Manifest.Layer("test4", 2, "rly"))
            )
          ))
    }
  }
}
