/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Lightbend, Inc.
 */

package com.lightbend.k8s.cli

import scopt.OptionParser
import argonaut._
import Argonaut._

object Main {
  case class InputArgs(foo: Option[String] = None)

  val defaultInputArgs = InputArgs()

  implicit def inputArgsCodecJson = casecodec1(InputArgs.apply, InputArgs.unapply)("foo")

  val parser = new OptionParser[InputArgs]("k8s-cli") {
    head("k8s-cli", "0.1.0")

    help("help").text("Print this help text")

    opt[String]('f', "foo")
      .text("test switch called foo")
      .action((v, c) => c.copy(foo = Some(v)))
  }

  def run(inputArgs: InputArgs): Unit = {
    println(s"Got input args: $inputArgs")

    val inputArgsJson = inputArgs.asJson
    println(s"Got input args as Argonaut JSON:")
    println(inputArgsJson)

    val inputArgsJsonString = inputArgsJson.spaces2
    println(s"Got input args as JSON string:")
    println(inputArgsJsonString)
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, defaultInputArgs).foreach(run)
  }
}
