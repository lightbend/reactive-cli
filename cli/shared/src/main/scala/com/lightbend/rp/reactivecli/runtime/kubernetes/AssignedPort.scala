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

package com.lightbend.rp.reactivecli.runtime.kubernetes

import com.lightbend.rp.reactivecli.annotations.Endpoint

/**
 * Represents [[Endpoint]] with its assigned [[port]] number.
 */
case class AssignedPort(endpoint: Endpoint, port: Int)

object AssignedPort {
  /**
   * If an endpoint port is undeclared, i.e. `0` it will be assigned a port number starting from this number.
   */
  private val AutoPortNumberStart = 10000

  /**
   * If an endpoint is undeclared, its port number will be of this value.
   */
  private val UndeclaredPortNumber = 0

  /**
   * Allocate port number to each endpoint based on:
   * - the endpoint's own declared port number, or,
   * - if the endpoint point is undeclared, it will be obtained based on the incremented value of
   * [[AutoPortNumberStart]].
   */
  def assignPorts(endpoints: Map[String, Endpoint]): Seq[AssignedPort] =
    endpoints.values.toList
      .foldLeft((AutoPortNumberStart, Seq.empty[AssignedPort])) { (acc, endpoint) =>
        val (autoPortNumberLast, endpointsAndAssignedPort) = acc

        val (autoPortNumberNext, assignedPort) =
          if (endpoint.port == UndeclaredPortNumber) {
            autoPortNumberLast + 1 -> autoPortNumberLast
          } else {
            autoPortNumberLast -> endpoint.port
          }

        autoPortNumberNext -> (endpointsAndAssignedPort :+ AssignedPort(endpoint, assignedPort))
      }
      ._2

}
