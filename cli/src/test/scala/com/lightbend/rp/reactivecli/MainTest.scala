/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Lightbend, Inc.
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
    }
  }
}
