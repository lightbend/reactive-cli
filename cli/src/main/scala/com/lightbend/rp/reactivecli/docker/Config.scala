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

import Argonaut._

case class Config(config: Config.Cfg)

object Config {
  case class Cfg(
    Hostname: Option[String] = None,
    ExposedPorts: Option[Map[String, Map[String, String]]] = None,
    Cmd: Option[Vector[String]] = None,
    Image: Option[String] = None,
    User: Option[String] = None,
    Labels: Option[Map[String, String]] = None)

  implicit val cfgCodec: CodecJson[Cfg] = CodecJson.derive[Cfg]
  implicit val configCodec: CodecJson[Config] = CodecJson.derive[Config]
}
