/*
 * Copyright 2018 Lightbend, Inc.
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

object MinVersionTest extends TestSuite {
  val tests = this{
    "Parse version" - {
      assert(Main.MinSupportedSbtReactiveApp.parseVersion("0.1.2") == Some((0, 1, 2)))
      assert(Main.MinSupportedSbtReactiveApp.parseVersion("1.0.0-SNAPSHOT") == Some((1, 0, 0)))
      assert(Main.MinSupportedSbtReactiveApp.parseVersion("2.4.8.0") == Some((2, 4, 8)))
      assert(Main.MinSupportedSbtReactiveApp.parseVersion("1.0") == None)
      assert(Main.MinSupportedSbtReactiveApp.parseVersion("0.x.1") == None)
      assert(Main.MinSupportedSbtReactiveApp.parseVersion("blah") == None)
    }
  }
}