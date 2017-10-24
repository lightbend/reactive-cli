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

package com.lightbend.rp.reactivecli.annotations

import scala.collection.immutable.Seq

object Check {
  sealed trait Port
  case class PortNumber(value: Int) extends Port
  case class ServiceName(value: String) extends Port
}

sealed trait Check

case class CommandCheck(command: Seq[String]) extends Check

object CommandCheck {
  def apply(command: String*): CommandCheck = new CommandCheck(command.toVector)
}

case class HttpCheck(port: Check.Port, intervalSeconds: Int, path: String) extends Check

object HttpCheck {
  def apply(port: Int, intervalSeconds: Int, path: String): HttpCheck =
    HttpCheck(Check.PortNumber(port), intervalSeconds, path)
  def apply(serviceName: String, intervalSeconds: Int, path: String): HttpCheck =
    HttpCheck(Check.ServiceName(serviceName), intervalSeconds, path)
}

case class TcpCheck(port: Check.Port, intervalSeconds: Int) extends Check

object TcpCheck {
  def apply(port: Int, intervalSeconds: Int): TcpCheck =
    TcpCheck(Check.PortNumber(port), intervalSeconds)
  def apply(serviceName: String, intervalSeconds: Int): TcpCheck =
    TcpCheck(Check.ServiceName(serviceName), intervalSeconds)
}