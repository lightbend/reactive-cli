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
import com.lightbend.rp.reactivecli.annotations.{ Annotations, LiteralEnvironmentVariable, Secret }
import com.lightbend.rp.reactivecli.argparse.CanaryDeploymentType
import com.lightbend.rp.reactivecli.process.jq
import scala.collection.immutable.Seq
import utest._

import Argonaut._

object JobJsonTest extends TestSuite {
  val annotations = Annotations(
    namespace = None,
    applications = Vector.empty,
    appName = Some("friendimpl"),
    appType = Some("basic"),
    configResource = None,
    diskSpace = None,
    memory = None,
    cpu = None,
    endpoints = Map.empty,
    secrets = Seq.empty,
    privileged = false,
    environmentVariables = Map.empty,
    version = Some("3.2.1-SNAPSHOT"),
    modules = Set.empty,
    akkaClusterBootstrapSystemName = None)

  val tests = this{
    "works" - {

      val job =
        Job.generate(
          annotations,
          "batch/v1",
          None,
          "test/testing:1.0.0",
          PodTemplate.ImagePullPolicy.Always,
          PodTemplate.RestartPolicy.Default,
          noOfReplicas = 1,
          Map.empty,
          CanaryDeploymentType,
          jq.jsonTransform,
          None,
          true)

      val json = job.toOption.get.json

      val expected = jObjectFields(
        "apiVersion" -> jString("batch/v1"),
        "kind" -> jString("Job"),
        "metadata" -> jObjectFields(
          "name" -> jString("friendimpl-v3-2-1-snapshot"),
          "labels" -> jObjectFields(
            "appName" -> jString("friendimpl"),
            "appNameVersion" -> jString("friendimpl-v3-2-1-snapshot"))),
        "spec" -> jObjectFields(
          "template" -> jObjectFields(
            "metadata" -> jObjectFields(
              "labels" -> jObjectFields(
                "appNameVersion" -> jString("friendimpl-v3-2-1-snapshot"))),
            "spec" -> jObjectFields(
              "restartPolicy" -> jString("OnFailure"),
              "containers" -> jArrayElements(
                jObjectFields(
                  "name" -> jString("friendimpl"),
                  "image" -> jString("test/testing:1.0.0"),
                  "ports" -> jEmptyArray,
                  "imagePullPolicy" -> jString("Always"),
                  "volumeMounts" -> jEmptyArray,
                  "env" -> jArrayElements(
                    jObjectFields("name" -> jString("RP_APP_NAME"), "value" -> jString("friendimpl")),
                    jObjectFields("name" -> jString("RP_APP_TYPE"), "value" -> jString("basic")),
                    jObjectFields("name" -> jString("RP_APP_VERSION"), "value" -> jString("3.2.1-SNAPSHOT")),
                    jObjectFields("name" -> jString("RP_ENDPOINTS_COUNT"), "value" -> jString("0")),
                    jObjectFields("name" -> jString("RP_KUBERNETES_POD_IP"), "valueFrom" -> jObjectFields("fieldRef" -> jObjectFields("fieldPath" -> jString("status.podIP")))),
                    jObjectFields("name" -> jString("RP_KUBERNETES_POD_NAME"), "valueFrom" -> jObjectFields("fieldRef" -> jObjectFields("fieldPath" -> jString("metadata.name")))),
                    jObjectFields("name" -> jString("RP_NAMESPACE"), "valueFrom" -> jObjectFields("fieldRef" -> jObjectFields("fieldPath" -> jString("metadata.namespace")))),
                    jObjectFields("name" -> jString("RP_PLATFORM"), "value" -> jString("kubernetes"))))),
              "volumes" -> jEmptyArray))))

      assert(json == expected)
    }

    "should fail when restart policy is wrong" - {
      val job =
        Job.generate(
          annotations,
          "batch/v1",
          None,
          "test/testing:1.0.0",
          PodTemplate.ImagePullPolicy.Always,
          PodTemplate.RestartPolicy.Always,
          noOfReplicas = 1,
          Map.empty,
          CanaryDeploymentType,
          jq.jsonTransform,
          None,
          true)

      assert(!job.toOption.isDefined)
    }
  }
}
