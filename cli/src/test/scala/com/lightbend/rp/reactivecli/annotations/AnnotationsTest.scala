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

import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.kubernetes.KubernetesArgs
import utest._

import scala.collection.immutable.Seq

object AnnotationsTest extends TestSuite {
  import Annotations._

  val tests = this{
    "decodeBoolean" - {
      assert(decodeBoolean("potato").isEmpty)
      assert(decodeBoolean("true").contains(true))
      assert(decodeBoolean("false").contains(false))
    }

    "decodeDouble" - {
      assert(decodeDouble("").isEmpty)
      assert(decodeDouble("potato").isEmpty)
      assert(decodeDouble("0.potato").isEmpty)
      assert(decodeDouble("0.").contains(0D))
      assert(decodeDouble("0").contains(0D))
      assert(decodeDouble("1.234").contains(1.234D))
      assert(decodeDouble("-1.234").contains(-1.234D))
    }

    "decodeInt" - {
      assert(decodeInt("").isEmpty)
      assert(decodeInt("potato").isEmpty)
      assert(decodeInt("0.potato").isEmpty)
      assert(decodeInt("0.").isEmpty)
      assert(decodeInt("0").contains(0))
      assert(decodeInt("1.234").isEmpty)
      assert(decodeInt("12345").contains(12345))
      assert(decodeInt(Int.MinValue.toString).contains(Int.MinValue))
      assert(decodeInt(Int.MaxValue.toString).contains(Int.MaxValue))
    }

    "decodeLong" - {
      assert(decodeLong("").isEmpty)
      assert(decodeLong("potato").isEmpty)
      assert(decodeLong("0.potato").isEmpty)
      assert(decodeLong("0.").isEmpty)
      assert(decodeLong("0").contains(0L))
      assert(decodeLong("1.234").isEmpty)
      assert(decodeLong("12345").contains(12345L))
      assert(decodeLong(Long.MinValue.toString).contains(Long.MinValue))
      assert(decodeLong(Long.MaxValue.toString).contains(Long.MaxValue))
    }

    "selectArray" - {
      "simple array" - {
        assert(
          selectArray(
            Map(
              "com.testing.1" -> "world", "com.testing.0" -> "hello", "com.testing.2" -> "!"),
            "com.testing")
            ==
            Vector(
              Map("" -> "hello"),
              Map("" -> "world"),
              Map("" -> "!")))
      }

      "nested map" - {
        assert(
          selectArray(
            Map(
              "com.testing.1.name" -> "jake",
              "com.testing.0.name" -> "steve",
              "com.testing.0.color" -> "red",
              "com.testing.1.color" -> "yellow"),
            "com.testing")
            ==
            Vector(
              Map("name" -> "steve", "color" -> "red"),
              Map("name" -> "jake", "color" -> "yellow")))
      }
    }

    "selectSubset" - {
      assert(
        selectSubset(
          Map(
            "com.testing.name" -> "world", "com.testing.color" -> "yellow", "com.testingother.color" -> "red"),
          "com.testing")
          ==
          Map(
            "name" -> "world",
            "color" -> "yellow"))
    }

    "Annotations.apply" - {
      assert(
        Annotations(Map.empty, GenerateDeploymentArgs()) == Annotations(
          namespace = None,
          appName = None,
          appType = None,
          diskSpace = None,
          memory = None,
          nrOfCpus = None,
          endpoints = Map.empty,
          secrets = Seq.empty,
          volumes = Map.empty,
          privileged = false,
          healthCheck = None,
          readinessCheck = None,
          environmentVariables = Map.empty,
          version = None,
          modules = Set.empty))

      "all options (except checks)" - {
        assert(
          Annotations(
            Map(
              "some.key" -> "test",
              "com.lightbend.rp.some-key" -> "test",

              "com.lightbend.rp.namespace" -> "fonts",
              "com.lightbend.rp.app-name" -> "my-app",
              "com.lightbend.rp.app-type" -> "basic",
              "com.lightbend.rp.app-version" -> "3.2.1-SNAPSHOT",
              "com.lightbend.rp.disk-space" -> "65536",
              "com.lightbend.rp.memory" -> "8192",
              "com.lightbend.rp.nr-of-cpus" -> "0.5",
              "com.lightbend.rp.privileged" -> "true",
              "com.lightbend.rp.environment-variables.0.type" -> "literal",
              "com.lightbend.rp.environment-variables.0.name" -> "testing1",
              "com.lightbend.rp.environment-variables.0.value" -> "testingvalue1",
              "com.lightbend.rp.environment-variables.0.some-key" -> "test",
              "com.lightbend.rp.environment-variables.1.type" -> "kubernetes.configMap",
              "com.lightbend.rp.environment-variables.1.name" -> "testing2",
              "com.lightbend.rp.environment-variables.1.map-name" -> "mymap",
              "com.lightbend.rp.environment-variables.1.key" -> "mykey",
              "com.lightbend.rp.environment-variables.2.type" -> "kubernetes.fieldRef",
              "com.lightbend.rp.environment-variables.2.name" -> "testing3",
              "com.lightbend.rp.environment-variables.2.field-path" -> "metadata.name",
              "com.lightbend.rp.volumes.0.type" -> "host-path",
              "com.lightbend.rp.volumes.0.path" -> "/my/host/path",
              "com.lightbend.rp.volumes.0.guest-path" -> "/my/guest/path/1",
              "com.lightbend.rp.volumes.0.some-key" -> "test",
              "com.lightbend.rp.volumes.1.type" -> "secret",
              "com.lightbend.rp.volumes.1.secret" -> "mysecret",
              "com.lightbend.rp.volumes.1.guest-path" -> "/my/guest/path/2",
              "com.lightbend.rp.endpoints.0.name" -> "ep1",
              "com.lightbend.rp.endpoints.0.protocol" -> "http",
              "com.lightbend.rp.endpoints.0.version" -> "9",
              "com.lightbend.rp.endpoints.0.ingress.0.type" -> "http",
              "com.lightbend.rp.endpoints.0.ingress.0.paths.0" -> "^/.*",
              "com.lightbend.rp.endpoints.0.ingress.0.ingress-ports.0" -> "80",
              "com.lightbend.rp.endpoints.0.ingress.0.ingress-ports.1" -> "443",
              "com.lightbend.rp.endpoints.0.ingress.0.hosts.0" -> "hello.com",
              "com.lightbend.rp.endpoints.0.some-key" -> "test",
              "com.lightbend.rp.endpoints.0.ingress.0.some-key" -> "test",
              "com.lightbend.rp.endpoints.1.name" -> "ep2",
              "com.lightbend.rp.endpoints.1.protocol" -> "tcp",
              "com.lightbend.rp.endpoints.1.version" -> "1",
              "com.lightbend.rp.endpoints.1.port" -> "1234",
              "com.lightbend.rp.endpoints.2.name" -> "ep3",
              "com.lightbend.rp.endpoints.2.protocol" -> "udp",
              "com.lightbend.rp.endpoints.2.port" -> "1234",
              "com.lightbend.rp.modules.common.enabled" -> "true",
              "com.lightbend.rp.modules.another-one.enabled" -> "false"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = Some("fonts"),
              appName = Some("my-app"),
              appType = Some("basic"),
              diskSpace = Some(65536L),
              memory = Some(8192L),
              nrOfCpus = Some(0.5D),
              endpoints = Map(
                "ep1" -> HttpEndpoint(0, "ep1", 0, Seq(HttpIngress(Seq(80, 443), Seq("hello.com"), Seq("^/.*")))),
                "ep2" -> TcpEndpoint(1, "ep2", 1234),
                "ep3" -> UdpEndpoint(2, "ep3", 1234)),
              secrets = Seq.empty,
              volumes = Map(
                "/my/guest/path/1" -> HostPathVolume("/my/host/path")),
              privileged = true,
              healthCheck = None,
              readinessCheck = None,
              environmentVariables = Map(
                "testing1" -> LiteralEnvironmentVariable("testingvalue1"),
                "testing2" -> kubernetes.ConfigMapEnvironmentVariable("mymap", "mykey"),
                "testing3" -> kubernetes.FieldRefEnvironmentVariable("metadata.name")),
              version = Some("3.2.1-SNAPSHOT"),
              modules = Set("common")))
      }

      "argument overrides" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.namespace" -> "tom",
              "com.lightbend.rp.disk-space" -> "65536",
              "com.lightbend.rp.memory" -> "8192",
              "com.lightbend.rp.nr-of-cpus" -> "0.5",
              "com.lightbend.rp.environment-variables.0.type" -> "literal",
              "com.lightbend.rp.environment-variables.0.name" -> "foo",
              "com.lightbend.rp.environment-variables.0.value" -> "bar"),
            GenerateDeploymentArgs(
              nrOfCpus = Some(0.5),
              memory = Some(1024),
              diskSpace = Some(2048),
              environmentVariables = Map(
                "foo" -> "foo",
                "hey" -> "there"),
              targetRuntimeArgs = Some(KubernetesArgs(
                namespace = Some("chirper"))))) == Annotations(
              namespace = Some("chirper"),
              appName = None,
              appType = None,
              diskSpace = Some(2048),
              memory = Some(1024),
              nrOfCpus = Some(0.5),
              endpoints = Map.empty,
              secrets = Seq.empty,
              volumes = Map.empty,
              privileged = false,
              healthCheck = None,
              readinessCheck = None,
              environmentVariables = Map(
                "foo" -> LiteralEnvironmentVariable("foo"),
                "hey" -> LiteralEnvironmentVariable("there")),
              version = None,
              modules = Set.empty))

      }

      "version (no label)" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.app-version" -> "3.2.1"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = None,
              appName = None,
              appType = None,
              diskSpace = None,
              memory = None,
              nrOfCpus = None,
              endpoints = Map.empty,
              secrets = Seq.empty,
              volumes = Map.empty,
              privileged = false,
              healthCheck = None,
              readinessCheck = None,
              environmentVariables = Map.empty,
              version = Some("3.2.1"),
              modules = Set.empty))
      }

      "endpoint (no version)" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.app-version" -> "3.2.1",

              "com.lightbend.rp.endpoints.1.name" -> "ep2",
              "com.lightbend.rp.endpoints.1.protocol" -> "tcp",
              "com.lightbend.rp.endpoints.1.port" -> "1234"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = None,
              appName = None,
              appType = None,
              diskSpace = None,
              memory = None,
              nrOfCpus = None,
              endpoints = Map(
                "ep2" -> TcpEndpoint(1, "ep2", 1234)),
              secrets = Seq.empty,
              volumes = Map.empty,
              privileged = false,
              healthCheck = None,
              readinessCheck = None,
              environmentVariables = Map.empty,
              version = Some("3.2.1"),
              modules = Set.empty))
      }

      "endpoint (no version and no app version)" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.endpoints.1.name" -> "ep2",
              "com.lightbend.rp.endpoints.1.protocol" -> "tcp",
              "com.lightbend.rp.endpoints.1.port" -> "1234"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = None,
              appName = None,
              appType = None,
              diskSpace = None,
              memory = None,
              nrOfCpus = None,
              endpoints = Map(
                "ep2" -> TcpEndpoint(1, "ep2", 1234)),
              secrets = Seq.empty,
              volumes = Map.empty,
              privileged = false,
              healthCheck = None,
              readinessCheck = None,
              environmentVariables = Map.empty,
              version = None,
              modules = Set.empty))
      }

      "CommandCheck" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.health-check.type" -> "command",
              "com.lightbend.rp.health-check.args.0" -> "/usr/bin/env",
              "com.lightbend.rp.health-check.args.1" -> "bash",
              "com.lightbend.rp.readiness-check.type" -> "command",
              "com.lightbend.rp.readiness-check.args.0" -> "/usr/bin/env",
              "com.lightbend.rp.readiness-check.args.1" -> "bash"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = None,
              appName = None,
              appType = None,
              diskSpace = None,
              memory = None,
              nrOfCpus = None,
              endpoints = Map.empty,
              secrets = Seq.empty,
              volumes = Map.empty,
              privileged = false,
              healthCheck = Some(CommandCheck("/usr/bin/env", "bash")),
              readinessCheck = Some(CommandCheck("/usr/bin/env", "bash")),
              environmentVariables = Map.empty,
              version = None,
              modules = Set.empty))
      }

      "HttpCheck" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.health-check.type" -> "http",
              "com.lightbend.rp.health-check.service-name" -> "my-service",
              "com.lightbend.rp.health-check.interval" -> "5",
              "com.lightbend.rp.health-check.path" -> "/hello",
              "com.lightbend.rp.readiness-check.type" -> "http",
              "com.lightbend.rp.readiness-check.port" -> "1234",
              "com.lightbend.rp.readiness-check.interval" -> "5",
              "com.lightbend.rp.readiness-check.path" -> "/hello"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = None,
              appName = None,
              appType = None,
              diskSpace = None,
              memory = None,
              nrOfCpus = None,
              endpoints = Map.empty,
              secrets = Seq.empty,
              volumes = Map.empty,
              privileged = false,
              healthCheck = Some(HttpCheck(Check.ServiceName("my-service"), 5, "/hello")),
              readinessCheck = Some(HttpCheck(Check.PortNumber(1234), 5, "/hello")),
              environmentVariables = Map.empty,
              version = None,
              modules = Set.empty))
      }

      "TcpCheck" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.health-check.type" -> "tcp",
              "com.lightbend.rp.health-check.service-name" -> "my-service",
              "com.lightbend.rp.health-check.interval" -> "5",
              "com.lightbend.rp.readiness-check.type" -> "tcp",
              "com.lightbend.rp.readiness-check.port" -> "1234",
              "com.lightbend.rp.readiness-check.interval" -> "5"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = None,
              appName = None,
              appType = None,
              diskSpace = None,
              memory = None,
              nrOfCpus = None,
              endpoints = Map.empty,
              secrets = Seq.empty,
              volumes = Map.empty,
              privileged = false,
              healthCheck = Some(TcpCheck(Check.ServiceName("my-service"), 5)),
              readinessCheck = Some(TcpCheck(Check.PortNumber(1234), 5)),
              environmentVariables = Map.empty,
              version = None,
              modules = Set.empty))
      }
    }
  }
}