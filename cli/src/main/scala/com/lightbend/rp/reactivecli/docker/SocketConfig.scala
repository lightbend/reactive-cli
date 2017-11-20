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

import argonaut._
import com.lightbend.rp.reactivecli.docker.{ Config => RegistryConfig }
import Argonaut._

case class SocketConfig(Config: SocketConfig.Cfg) {
  def registryConfig: RegistryConfig = RegistryConfig(RegistryConfig.Cfg(Labels = Config.Labels))
}

object SocketConfig {
  case class Cfg(Labels: Option[Map[String, String]] = None)

  implicit val cfgCodec: CodecJson[Cfg] = CodecJson.derive[Cfg]
  implicit val configCodec: CodecJson[SocketConfig] = CodecJson.derive[SocketConfig]
}
