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

import libhttpsimple.{ LibHttpSimple, HttpRequest }
import scopt.OptionParser
import slogging._
import argonaut._
import Argonaut._

object Main extends LazyLogging {
  object LogLevels extends Enumeration {
    type Level = Value

    val error, warn, info, debug, trace = Value
  }

  implicit val logLevelsRead: scopt.Read[LogLevels.Value] =
    scopt.Read.reads(LogLevels.withName)

  case class InputArgs(foo: Option[String] = None, logLevel: LogLevels.Value = LogLevels.info)

  val defaultInputArgs = InputArgs()

  val parser = new OptionParser[InputArgs]("reactive-cli") {
    head("reactive-cli", "0.1.0")

    help("help").text("Print this help text")

    opt[String]('f', "foo")
      .text("test switch called foo")
      .action((v, c) => c.copy(foo = Some(v)))

    opt[LogLevels.Value]('l', "loglevel")
      .text("Sets the log level. Available: error, warn, info, debug, trace")
      .action((v, c) => c.copy(logLevel = v))
  }

  def main(args: Array[String]): Unit = {
    LibHttpSimple.globalInit()
    LoggerConfig.factory = TerminalLoggerFactory

    try {
      parser.parse(args, defaultInputArgs).foreach { inputArgs =>
        LoggerConfig.level =
          inputArgs.logLevel match {
            case LogLevels.`error` => LogLevel.ERROR
            case LogLevels.`warn` => LogLevel.WARN
            case LogLevels.`info` => LogLevel.INFO
            case LogLevels.`debug` => LogLevel.DEBUG
            case LogLevels.`trace` => LogLevel.TRACE
          }

        logger.debug(s"input args: $inputArgs")

        println(s"Got input args: $inputArgs")

        val response = LibHttpSimple(HttpRequest("https://www.example.org"))
        println(s"Got HTTP response:")
        println(response)

        println(docker.DockerRegistry.getConfig("dockercloud/hello-world", token = None))
      }
    } finally {
      LibHttpSimple.globalCleanup()
    }
  }
}
