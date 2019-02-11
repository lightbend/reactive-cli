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

package object runtime {
  private[reactivecli] val AkkaClusterMinimumReplicas = 2
  private[reactivecli] val ReadyCheckUrl = "/platform-tooling/ready"
  private[reactivecli] val HealthCheckUrl = "/platform-tooling/healthy"
  private[reactivecli] val playPidDevNull = "-Dplay.server.pidfile.path=/dev/null"

  def pathDepthAndLength(path: String): (Int, Int) = {
    val depth = path.split('/').length
    val length = path.length
    depth -> length
  }
}
