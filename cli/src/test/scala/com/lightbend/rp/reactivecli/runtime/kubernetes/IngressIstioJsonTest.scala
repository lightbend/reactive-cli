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

import argonaut._
import Argonaut._
import com.lightbend.rp.reactivecli.annotations._
import utest._

import scala.collection.immutable.Seq

object IngressIstioJsonTest extends TestSuite {
  val annotations = Annotations(
    appName = Some("friendimpl"),
    diskSpace = Some(65536L),
    memory = Some(8192L),
    nrOfCpus = Some(0.5D),
    endpoints = Map(
      "ep1" -> HttpEndpoint("ep1", 1234, version = Some(1), acls = Seq(HttpEndpoint.HttpAcl("/api/friend")))),
    volumes = Map(
      "/my/guest/path/1" -> HostPathVolume("/my/host/path"),
      "/my/guest/path/2" -> SecretVolume("mysecret")),
    privileged = true,
    healthCheck = None,
    readinessCheck = None,
    environmentVariables = Map(
      "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
    version = Some(Version(3, 2, 1, Some("SNAPSHOT"))))

  val tests = this{
    "json serialization" - {
      "with secret tls and ssl redirect" - {
        val generatedJson = IngressIstio.generate(annotations).get
        val expectedJson =
          """
            |{
            |  "apiVersion": "extensions/v1beta1",
            |  "kind": "Ingress",
            |  "metadata": {
            |    "name": "friendimpl",
            |    "annotations": {
            |      "kubernetes.io/ingress.class": "istio"
            |    }
            |  },
            |  "spec": {
            |    "rules": [
            |      {
            |        "http": {
            |          "paths": [
            |            {
            |              "path": "/api/friend.*",
            |              "backend": {
            |                "serviceName": "ep1-v1",
            |                "servicePort": 1234
            |              }
            |            }
            |          ]
            |        }
            |      }
            |    ]
            |  }
            |}
          """.stripMargin.parse.right.get
        assert(generatedJson == expectedJson)
      }

      "should fail if application name is not defined" - {
        assert(IngressIstio.generate(annotations.copy(appName = None)).isFailure)
      }

    }

  }
}
