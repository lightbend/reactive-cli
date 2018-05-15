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

package com.lightbend.rp.reactivecli.process

import scala.concurrent.Future
import com.lightbend.rp.reactivecli.concurrent._

import com.lightbend.rp.reactivecli.docker.Config
import argonaut.CodecJson

object docker {
  def inspectImageForConfig(imageName: String): Future[Option[Config]] =
    exec("docker", "inspect", imageName).map {
      case (_, json) =>
        import _root_.argonaut.Argonaut._
        json.decodeOption[List[CmdConfig]].flatMap(_.headOption.map(_.registryConfig))
    }

  import com.lightbend.rp.reactivecli.docker.{ Config => RegistryConfig }

  private case class CmdConfig(Config: RegistryConfig.Cfg) {
    def registryConfig: RegistryConfig = RegistryConfig(Config)
  }

  private object CmdConfig {
    implicit val configUpperCodec: CodecJson[CmdConfig] = CodecJson.derive[CmdConfig]
  }
}
