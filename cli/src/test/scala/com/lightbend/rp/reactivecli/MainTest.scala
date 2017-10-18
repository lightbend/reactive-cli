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

object MainTest extends TestSuite {
  val tests = this{
    "argument parsing" - {
      "with default arguments" - {
        val result = Main.parser.parse(Seq.empty, Main.defaultInputArgs)
        assert(result.contains(Main.defaultInputArgs))
      }

      "-f" - {
        val result = Main.parser.parse(Seq("-f", "hey"), Main.defaultInputArgs)
        assert(result.contains(Main.InputArgs(foo = Some("hey"))))
      }

      "--foo" - {
        val result = Main.parser.parse(Seq("--foo", "hey"), Main.defaultInputArgs)
        assert(result.contains(Main.InputArgs(foo = Some("hey"))))
      }

      "--env" - {
        val result1 = Main.parser.parse(Seq("--env", "test1=test2"), Main.defaultInputArgs)
        assert(result1.contains(Main.InputArgs(environmentVariables = Map("test1" -> "test2"))))

        val result2 = Main.parser.parse(Seq("--env", "test1"), Main.defaultInputArgs)
        assert(result2.contains(Main.InputArgs(environmentVariables = Map("test1" -> ""))))
      }

      "--nr-of-cpus" - {
        val result = Main.parser.parse(Seq("--nr-of-cpus", "0.5"), Main.defaultInputArgs)
        assert(result.contains(Main.InputArgs(nrOfCpus = Some(0.5))))
      }

      "--memory" - {
        val result = Main.parser.parse(Seq("--memory", "1024"), Main.defaultInputArgs)
        assert(result.contains(Main.InputArgs(memory = Some(1024))))
      }

      "--disk-space" - {
        val result = Main.parser.parse(Seq("--disk-space", "2048"), Main.defaultInputArgs)
        assert(result.contains(Main.InputArgs(diskSpace = Some(2048))))
      }
    }
  }
}
