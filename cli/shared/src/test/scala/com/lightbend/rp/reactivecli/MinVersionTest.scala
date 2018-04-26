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

object MinVersionTest extends TestSuite {
  val tests = this{
    "Parse version" - {
      assert(Main.parseVersion("0.1.2") == Some((0, 1, 2)))
      assert(Main.parseVersion("1.0.0-SNAPSHOT") == Some((1, 0, 0)))
      assert(Main.parseVersion("2.4.8.0") == Some((2, 4, 8)))
      assert(Main.parseVersion("1.0") == None)
      assert(Main.parseVersion("0.x.1") == None)
      assert(Main.parseVersion("blah") == None)
    }
    "Validate given version" - {
      assert(!Main.isVersionValid("0.1.2", 2, 0))
      assert(!Main.isVersionValid("blah", 0, 0))
      assert(Main.isVersionValid("0.1.2", 0, 1))
      assert(Main.isVersionValid("0.1.0", 0, 1))
      assert(Main.isVersionValid("1.0.0", 0, 1))
      assert(Main.isVersionValid("0.1.2-SNAPSHOT", 0, 1))
    }
  }
}
