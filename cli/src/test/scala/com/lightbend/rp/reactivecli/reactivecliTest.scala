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

package com.lightbend.rp.reactivecli

import utest._

object reactivecliTest extends TestSuite {
  val tests = this{
    "someIf" - {
      "short circuits" - {
        var called = false

        someIf(b = false) { called = true}

        assert(!called)
      }

      "works" - {
        assert(someIf(b = true)("ok").contains("ok"))
        assert(someIf(b = false)("ok").isEmpty)
      }
    }
  }
}
