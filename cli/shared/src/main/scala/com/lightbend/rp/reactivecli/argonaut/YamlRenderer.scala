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

import argonaut.Argonaut._
import argonaut._

object YamlRenderer {
  private val Indent = "  "
  private val IndentArray = "- "

  def render(j: Json): String = {
    def string(string: JsonString): String =
      if (needsQuotes(string))
        jString(string).nospaces
      else
        string

    def bool(b: Boolean): String =
      if (b) "true" else "false"

    def `null`: String =
      "null"

    def number(number: JsonNumber): String =
      number.asJson.nospaces

    def nonEmptyArrayOrObject(j: Json): Boolean =
      (j.isArray && j.array.get.nonEmpty) || (j.isObject && j.obj.get.isNotEmpty)

    def array(array: JsonArray, level: Int): String =
      if (array.isEmpty)
        "[]"
      else
        array
          .map { element =>
            val rendered = render(element, level + 1)

            if (nonEmptyArrayOrObject(element)) {
              val initialSpaces = spaces(level + 1)
              val replacement = spaces(level) + IndentArray
              val fixed = rendered.replaceFirst(initialSpaces, replacement)

              fixed
            } else {
              spaces(level) + IndentArray + rendered
            }
          }
          .mkString("\n")

    def obj(obj: JsonObject, level: Int): String =
      if (obj.isEmpty)
        "{}"
      else
        obj
          .toList
          .map {
            case (field, value) =>
              val renderedField = string(field)
              val rendered = render(value, level + 1)
              val separator =
                if (nonEmptyArrayOrObject(value))
                  ":\n"
                else
                  ": "

              spaces(level) + renderedField + separator + rendered
          }
          .mkString("\n")

    def render(json: Json, level: Int): String = {
      if (json.isString)
        string(json.string.get)
      else if (json.isBool)
        bool(json.bool.get)
      else if (json.isNull)
        `null`
      else if (json.isNumber)
        number(json.number.get)
      else if (json.isArray)
        array(json.array.get, level)
      else if (json.isObject)
        obj(json.obj.get, level)
      else
        throw new IllegalArgumentException(s"Unexpected state for $json")
    }

    render(json = j, level = 0)
  }

  private def spaces(level: Int): String = Indent * level

  private def needsQuotes(string: String) =
    string.isEmpty || string.trim != string || !string.matches("^[A-Za-z][A-Za-z0-9 ]*$")
}