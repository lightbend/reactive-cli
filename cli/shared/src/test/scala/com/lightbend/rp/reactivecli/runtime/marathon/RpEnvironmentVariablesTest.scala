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

package com.lightbend.rp.reactivecli.runtime.marathon

import argonaut.Argonaut._
import argonaut._
import com.lightbend.rp.reactivecli.annotations.{ Annotations, LiteralEnvironmentVariable, TcpEndpoint }
import com.lightbend.rp.reactivecli.argparse.CanaryDeploymentType
import utest._

import scala.collection.immutable.Seq

object RpEnvironmentVariablesTest extends TestSuite {
  val tests = this{
    "namespace" - {
      assert(
        RpEnvironmentVariables
          .envs(
            Some("test"),
            Annotations(
              namespace = None,
              applications = Vector.empty,
              appName = Some("friendimpl"),
              appType = None,
              configResource = None,
              diskSpace = Some(65536L),
              memory = Some(8192L),
              cpu = Some(0.5D),
              endpoints = Map(
                "ep1" -> TcpEndpoint(0, "ep1", 1234)),
              secrets = Seq.empty,
              privileged = true,
              environmentVariables = Map(
                "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
              version = Some("3.2.1-SNAPSHOT"),
              modules = Set.empty,
              akkaClusterBootstrapSystemName = None),
            "friendimpl",
            1,
            Map.empty,
            akkaClusterJoinExisting = false)
          .get("RP_NAMESPACE")
          .contains("test"))
    }

    "basic" - {
      val expected =
        Map(
          "RP_ENDPOINT_0_BIND_PORT" -> "$PORT_EP",
          "RP_ENDPOINTS" -> "EP1",
          "RP_APP_VERSION" -> "3.2.1-SNAPSHOT",
          "RP_PLATFORM" -> "mesos",
          "RP_ENDPOINT_EP1_PORT" -> "$PORT_EP",
          "RP_APP_NAME" -> "friendimpl",
          "RP_ENDPOINT_0_PORT" -> "$PORT_EP",
          "RP_ENDPOINT_EP1_BIND_PORT" -> "$PORT_EP",
          "RP_ENDPOINT_0_BIND_HOST" -> "0.0.0.0",
          "RP_ENDPOINT_0_HOST" -> "$HOST",
          "RP_ENDPOINTS_COUNT" -> "1",
          "RP_ENDPOINT_EP1_HOST" -> "$HOST",
          "RP_ENDPOINT_EP1_BIND_HOST" -> "0.0.0.0")

      val actual =
        RpEnvironmentVariables
          .envs(
            None,
            Annotations(
              namespace = None,
              applications = Vector.empty,
              appName = Some("friendimpl"),
              appType = None,
              configResource = None,
              diskSpace = Some(65536L),
              memory = Some(8192L),
              cpu = Some(0.5D),
              endpoints = Map(
                "ep1" -> TcpEndpoint(0, "ep1", 1234)),
              secrets = Seq.empty,
              privileged = true,
              environmentVariables = Map(
                "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
              version = Some("3.2.1-SNAPSHOT"),
              modules = Set.empty,
              akkaClusterBootstrapSystemName = None),
            "friendimpl",
            1,
            Map.empty,
            akkaClusterJoinExisting = false)

      assert(expected == actual)
    }

    "akka cluster" - {
      val expected =
        Map(
          "RP_ENDPOINT_0_BIND_PORT" -> "$PORT_EP",
          "RP_ENDPOINTS" -> "EP1", "RP_APP_VERSION" -> "3.2.1-SNAPSHOT",
          "RP_JAVA_OPTS" -> "-Dakka.management.cluster.bootstrap.contact-point-discovery.discovery-method=marathon-api -Dakka.management.cluster.bootstrap.contact-point-discovery.effective-name=friendimpl -Dakka.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr=3 -Dakka.discovery.marathon-api.app-label-query=APP_NAME==%s",
          "RP_PLATFORM" -> "mesos",
          "RP_ENDPOINT_EP1_PORT" -> "$PORT_EP",
          "RP_MODULES" -> "akka-cluster-bootstrapping",
          "RP_APP_NAME" -> "friendimpl",
          "RP_ENDPOINT_0_PORT" -> "$PORT_EP",
          "RP_ENDPOINT_EP1_BIND_PORT" -> "$PORT_EP",
          "RP_ENDPOINT_0_BIND_HOST" -> "0.0.0.0",
          "RP_ENDPOINT_0_HOST" -> "$HOST",
          "RP_ENDPOINTS_COUNT" -> "1",
          "RP_ENDPOINT_EP1_HOST" -> "$HOST",
          "RP_ENDPOINT_EP1_BIND_HOST" -> "0.0.0.0",
          "RP_NAMESPACE" -> "test")

      val actual =
        RpEnvironmentVariables
          .envs(
            Some("test"),
            Annotations(
              namespace = Some("chirper"),
              applications = Vector.empty,
              appName = Some("friendimpl"),
              appType = None,
              configResource = None,
              diskSpace = Some(65536L),
              memory = Some(8192L),
              cpu = Some(0.5D),
              endpoints = Map(
                "ep1" -> TcpEndpoint(0, "ep1", 1234)),
              secrets = Seq.empty,
              privileged = true,
              environmentVariables = Map(
                "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
              version = Some("3.2.1-SNAPSHOT"),
              modules = Set("akka-cluster-bootstrapping"),
              akkaClusterBootstrapSystemName = None),
            "friendimpl",
            noOfReplicas = 3,
            Map.empty,
            akkaClusterJoinExisting = false)

      assert(expected == actual)
    }

    "external services" - {
      val expected =
        Map(
          "RP_ENDPOINT_0_BIND_PORT" -> "$PORT_EP",
          "RP_ENDPOINTS" -> "EP1",
          "RP_APP_VERSION" -> "3.2.1-SNAPSHOT",
          "RP_PLATFORM" -> "mesos",
          "RP_ENDPOINT_EP1_PORT" -> "$PORT_EP",
          "RP_APP_NAME" -> "friendimpl",
          "RP_ENDPOINT_0_PORT" -> "$PORT_EP",
          "RP_ENDPOINT_EP1_BIND_PORT" -> "$PORT_EP",
          "RP_ENDPOINT_0_BIND_HOST" -> "0.0.0.0",
          "RP_ENDPOINT_0_HOST" -> "$HOST",
          "RP_ENDPOINTS_COUNT" -> "1",
          "RP_ENDPOINT_EP1_HOST" -> "$HOST",
          "RP_ENDPOINT_EP1_BIND_HOST" -> "0.0.0.0",
          "RP_MODULES" -> "service-discovery",
          "RP_JAVA_OPTS" -> "-Dcom.lightbend.platform-tooling.service-discovery.external-service-addresses.chirpservice.0=_chirpservice._tcp.marathon.mesos -Dcom.lightbend.platform-tooling.service-discovery.external-service-addresses.chirpservice.1=_chirpservice._udp.marathon.mesos -Dcom.lightbend.platform-tooling.service-discovery.external-service-addresses.friendservice.0=_friendservice._tcp.marathon.mesos")

      val actual =
        RpEnvironmentVariables
          .envs(
            None,
            Annotations(
              namespace = None,
              applications = Vector.empty,
              appName = Some("friendimpl"),
              appType = None,
              configResource = None,
              diskSpace = Some(65536L),
              memory = Some(8192L),
              cpu = Some(0.5D),
              endpoints = Map(
                "ep1" -> TcpEndpoint(0, "ep1", 1234)),
              secrets = Seq.empty,
              privileged = true,
              environmentVariables = Map(
                "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
              version = Some("3.2.1-SNAPSHOT"),
              modules = Set("service-discovery"),
              akkaClusterBootstrapSystemName = None),
            "friendimpl",
            1,
            Map(
              "chirpservice" -> Seq("_chirpservice._tcp.marathon.mesos", "_chirpservice._udp.marathon.mesos"),
              "friendservice" -> Seq("_friendservice._tcp.marathon.mesos")),
            akkaClusterJoinExisting = false)

      assert(expected == actual)
    }
  }
}
