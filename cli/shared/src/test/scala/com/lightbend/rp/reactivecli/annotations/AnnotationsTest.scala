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
          applications = Vector.empty,
          appName = None,
          appType = None,
          configResource = None,
          diskSpace = None,
          memory = None,
          cpu = None,
          endpoints = Map.empty,
          managementEndpointName = None,
          remotingEndpointName = None,
          secrets = Seq.empty,
          privileged = false,
          environmentVariables = Map.empty,
          version = None,
          modules = Set.empty,
          akkaClusterBootstrapSystemName = None))

      "all options" - {
        assert(
          Annotations(
            Map(
              "some.key" -> "test",
              "com.lightbend.rp.some-key" -> "test",

              "com.lightbend.rp.app-name" -> "my-app",
              "com.lightbend.rp.app-type" -> "basic",
              "com.lightbend.rp.app-version" -> "3.2.1-SNAPSHOT",
              "com.lightbend.rp.applications.0.name" -> "test",
              "com.lightbend.rp.applications.0.arguments.0" -> "arg0",
              "com.lightbend.rp.applications.0.arguments.1" -> "arg1",
              "com.lightbend.rp.applications.1.name" -> "test2",
              "com.lightbend.rp.applications.1.arguments.0" -> "arrrg0",
              "com.lightbend.rp.applications.1.arguments.1" -> "arrrg1",
              "com.lightbend.rp.config-resource" -> "my-app.conf",
              "com.lightbend.rp.disk-space" -> "65536",
              "com.lightbend.rp.memory" -> "8192",
              "com.lightbend.rp.cpu" -> "0.5",
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
              "com.lightbend.rp.remoting-endpoint" -> "remoting",
              "com.lightbend.rp.management-endpoint" -> "management",
              "com.lightbend.rp.annotations.0.key" -> "annotationKey0",
              "com.lightbend.rp.annotations.0.value" -> "annotationValue0",
              "com.lightbend.rp.annotations.1.key" -> "annotationKey1",
              "com.lightbend.rp.annotations.1.value" -> "annotationValue1",
              "com.lightbend.rp.modules.common.enabled" -> "true",
              "com.lightbend.rp.modules.another-one.enabled" -> "false",
              "com.lightbend.rp.akka-cluster-bootstrap.system-name" -> "test"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = None,
              applications = Vector(
                "test" -> Vector("arg0", "arg1"),
                "test2" -> Vector("arrrg0", "arrrg1")),
              appName = Some("my-app"),
              appType = Some("basic"),
              configResource = Some("my-app.conf"),
              diskSpace = Some(65536L),
              memory = Some(8192L),
              cpu = Some(0.5D),
              endpoints = Map(
                "ep1" -> HttpEndpoint(0, "ep1", 0, Seq(HttpIngress(Seq(80, 443), Seq("hello.com"), Seq("^/.*")))),
                "ep2" -> TcpEndpoint(1, "ep2", 1234),
                "ep3" -> UdpEndpoint(2, "ep3", 1234)),
              managementEndpointName = Some("management"),
              remotingEndpointName = Some("remoting"),
              secrets = Seq.empty,
              annotations = Vector(
                Annotation("annotationKey0", "annotationValue0"),
                Annotation("annotationKey1", "annotationValue1")),
              privileged = true,
              environmentVariables = Map(
                "testing1" -> LiteralEnvironmentVariable("testingvalue1"),
                "testing2" -> kubernetes.ConfigMapEnvironmentVariable("mymap", "mykey"),
                "testing3" -> kubernetes.FieldRefEnvironmentVariable("metadata.name")),
              version = Some("3.2.1-SNAPSHOT"),
              modules = Set("common"),
              akkaClusterBootstrapSystemName = Some("test")))
      }

      "argument overrides" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.disk-space" -> "65536",
              "com.lightbend.rp.memory" -> "8192",
              "com.lightbend.rp.cpu" -> "0.5",
              "com.lightbend.rp.environment-variables.0.type" -> "literal",
              "com.lightbend.rp.environment-variables.0.name" -> "foo",
              "com.lightbend.rp.environment-variables.0.value" -> "bar"),
            GenerateDeploymentArgs(
              cpu = Some(0.5),
              memory = Some(1024),
              diskSpace = Some(2048),
              environmentVariables = Map(
                "foo" -> "foo",
                "hey" -> "there"),
              targetRuntimeArgs = Some(KubernetesArgs(
                namespace = Some("chirper"))))) == Annotations(
              namespace = Some("chirper"),
              applications = Vector.empty,
              appName = None,
              appType = None,
              configResource = None,
              diskSpace = Some(2048),
              memory = Some(1024),
              cpu = Some(0.5),
              endpoints = Map.empty,
              managementEndpointName = None,
              remotingEndpointName = None,
              secrets = Seq.empty,
              privileged = false,
              environmentVariables = Map(
                "foo" -> LiteralEnvironmentVariable("foo"),
                "hey" -> LiteralEnvironmentVariable("there")),
              version = None,
              modules = Set.empty,
              akkaClusterBootstrapSystemName = None))

      }

      "version (no label)" - {
        assert(
          Annotations(
            Map(
              "com.lightbend.rp.app-version" -> "3.2.1"),
            GenerateDeploymentArgs()) == Annotations(
              namespace = None,
              applications = Vector.empty,
              appName = None,
              appType = None,
              configResource = None,
              diskSpace = None,
              memory = None,
              cpu = None,
              endpoints = Map.empty,
              managementEndpointName = None,
              remotingEndpointName = None,
              secrets = Seq.empty,
              privileged = false,
              environmentVariables = Map.empty,
              version = Some("3.2.1"),
              modules = Set.empty,
              akkaClusterBootstrapSystemName = None))
      }

      "name (argument override)" - {
        assert(
          Annotations(
            Map("com.lightbend.rp.name" -> "test1"),
            GenerateDeploymentArgs(name = Some("test2"))) == Annotations(
              namespace = None,
              applications = Vector.empty,
              appName = Some("test2"),
              appType = None,
              configResource = None,
              diskSpace = None,
              memory = None,
              cpu = None,
              endpoints = Map.empty,
              managementEndpointName = None,
              remotingEndpointName = None,
              secrets = Seq.empty,
              privileged = false,
              environmentVariables = Map.empty,
              version = None,
              modules = Set.empty,
              akkaClusterBootstrapSystemName = None))
      }

      "version (argument override)" - {
        assert(
          Annotations(
            Map("com.lightbend.rp.app-version" -> "3.2.1"),
            GenerateDeploymentArgs(version = Some("2.1.3"))) == Annotations(
              namespace = None,
              applications = Vector.empty,
              appName = None,
              appType = None,
              configResource = None,
              diskSpace = None,
              memory = None,
              cpu = None,
              endpoints = Map.empty,
              managementEndpointName = None,
              remotingEndpointName = None,
              secrets = Seq.empty,
              privileged = false,
              environmentVariables = Map.empty,
              version = Some("2.1.3"),
              modules = Set.empty,
              akkaClusterBootstrapSystemName = None))
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
              applications = Vector.empty,
              appName = None,
              appType = None,
              configResource = None,
              diskSpace = None,
              memory = None,
              cpu = None,
              endpoints = Map(
                "ep2" -> TcpEndpoint(1, "ep2", 1234)),
              managementEndpointName = None,
              remotingEndpointName = None,
              secrets = Seq.empty,
              privileged = false,
              environmentVariables = Map.empty,
              version = Some("3.2.1"),
              modules = Set.empty,
              akkaClusterBootstrapSystemName = None))
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
              applications = Vector.empty,
              appName = None,
              appType = None,
              configResource = None,
              diskSpace = None,
              memory = None,
              cpu = None,
              endpoints = Map(
                "ep2" -> TcpEndpoint(1, "ep2", 1234)),
              managementEndpointName = None,
              remotingEndpointName = None,
              secrets = Seq.empty,
              privileged = false,
              environmentVariables = Map.empty,
              version = None,
              modules = Set.empty,
              akkaClusterBootstrapSystemName = None))
      }
    }
  }
}
