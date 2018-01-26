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

package com.lightbend.rp.reactivecli.argonaut

import argonaut._
import utest._

import Argonaut._

object YamlRendererTest extends TestSuite {
  import YamlRenderer.render

  val tests = this {
    def equal[A, B](a: A, b: B) = assert(a == b)

    "render" - {
      "empty arrays" - equal(render(jEmptyArray), "[]")

      "empty objects" - equal(render(jEmptyObject), "{}")

      "empty string" - equal(render(jEmptyString), "\"\"")

      "zero" - equal(render(jZero), "0")

      "null" - equal(render(jNull), "null")

      "true" - equal(render(jTrue), "true")

      "false" - equal(render(jFalse), "false")

      "numeric arrays" - equal(
        render(jArrayElements(jNumber(1), jNumber(2), jNumber(3))),

        """|- 1
           |- 2
           |- 3""".stripMargin
      )

      "numeric string" - equal(render(jString("12345")), "\"12345\"")

      "simple string" - equal(render(jString("hello world")), "hello world")

      "complex string" - equal(render(jString("hello \"world!\"")), "\"hello \\\"world!\\\"\"")

      "simple object" - equal(
        render(jObjectFields("name" -> jString("jason"), "age" -> jNumber(100))),
        "name: jason\nage: 100"
      )

      "array of objects" - equal(
        render(
          jArrayElements(
            jObjectFields("name" -> jString("john!"), "age" -> jNumber(100), "present?" -> jFalse),
            jObjectFields("name" -> jString("jessica"), "age" -> jNumber(101), "present?" -> jTrue)
          )
        ),

        """|- name: "john!"
           |  age: 100
           |  "present?": false
           |- name: jessica
           |  age: 101
           |  "present?": true""".stripMargin
      )

      "object containing array of objects" - equal(
        render(
          jObjectFields("people" -> jArrayElements(
            jObjectFields("name" -> jString("john"), "age" -> jNumber(100)),
            jObjectFields("name" -> jString("jessica"), "age" -> jNumber(101))
          ))
        ),

        """|people:
           |  - name: john
           |    age: 100
           |  - name: jessica
           |    age: 101""".stripMargin
      )

      "nested arrays" - equal(
        render(
          jArrayElements(
            jArrayElements(jNumber(1), jNumber(2), jNumber(3)),
            jArrayElements(jNumber(4), jNumber(5), jNumber(6)),
            jArrayElements(jNumber(7), jNumber(8), jNumber(9))
          )
        ),


        """|- - 1
           |  - 2
           |  - 3
           |- - 4
           |  - 5
           |  - 6
           |- - 7
           |  - 8
           |  - 9""".stripMargin
      )

      "nested objects" - equal(
        render(
          jObjectFields("spec" -> jObjectFields(
            "template" -> jObjectFields(
              "spec" -> jObjectFields(
                "one" -> jNumber(1),
                "two" -> jNumber(2),
                "three" -> jObjectFields(
                  "a" -> jArrayElements(jFalse),
                  "b" -> jArrayElements(jTrue)
                )
              )
            )
          ))
        ),

        """|spec:
           |  template:
           |    spec:
           |      one: 1
           |      two: 2
           |      three:
           |        a:
           |          - false
           |        b:
           |          - true""".stripMargin
      )
    }
  }
}
