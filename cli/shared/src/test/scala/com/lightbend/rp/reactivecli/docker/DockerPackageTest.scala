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

import utest._

object DockerPackageTest extends TestSuite {
  val tests = this {
    "exact match" - {
      assert(registryAuthNameMatches("test.registry.com", "test.registry.com"))
    }

    "DockerHub overwrite" - {
      assert(registryAuthNameMatches("registry.hub.docker.com", "https://index.docker.io/v1/"))
      assert(registryAuthNameMatches("registry.hub.docker.com", "index.docker.io/v1/"))
    }

    "https match" - {
      assert(registryAuthNameMatches("test.registry.com", "https://test.registry.com"))
    }

    "not matching" - {
      assert(!registryAuthNameMatches("test.registry.info", "test.registry.com"))
      assert(!registryAuthNameMatches("test2.registry.info", "test.registry.info"))
    }
  }
}
