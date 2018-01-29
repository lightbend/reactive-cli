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

package object docker {
  val DockerAcceptManifestHeader = "application/vnd.docker.distribution.manifest.v2+json"
  val DockerDefaultRegistry = "registry.hub.docker.com"
  val DockerDefaultLibrary = "library"
  val DockerDefaultTag = "latest"

  // Sometimes authentication credentials store different address than our default server,
  // this function handles these discrepancies.
  def registryAuthNameMatches(registry: String, authRealm: String) : Boolean = {
    if (registry == authRealm)
      true
    else if (registry == DockerDefaultRegistry && authRealm == "https://index.docker.io/v1/")
      true
    else
      false
  }
}
