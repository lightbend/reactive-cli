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

import scala.util.Success
import utest._

object DockerRegistryTest extends TestSuite {
  val tests = this{
    "blobUrl" - {
      assert(
        DockerRegistry.blobUrl(
          Image(
            DockerDefaultRegistry,
            DockerDefaultLibrary,
            "alpine",
            "3.5",
            None,
            None,
            "alpine",
            Some("3.5")), "abc123") == s"https://$DockerDefaultRegistry/v2/$DockerDefaultLibrary/alpine/blobs/abc123"
      )
    }

    "manifestUrl" - {
      assert(
        DockerRegistry.manifestUrl(
          Image(
            DockerDefaultRegistry,
            DockerDefaultLibrary,
            "alpine",
            "3.5",
            None,
            None,
            "alpine",
            Some("3.5"))) == s"https://$DockerDefaultRegistry/v2/$DockerDefaultLibrary/alpine/manifests/3.5"
      )
    }

    "parseImageUri" - {
      assert(
        DockerRegistry.parseImageUri("alpine") ==
          Success(
            Image(
              DockerDefaultRegistry,
              DockerDefaultLibrary,
              "alpine",
              "latest",
              None,
              None,
              "alpine",
              None
            )))

      assert(
        DockerRegistry.parseImageUri("alpine:3.5") ==
          Success(
            Image(
              DockerDefaultRegistry,
              DockerDefaultLibrary,
              "alpine",
              "3.5",
              None,
              None,
              "alpine",
              Some("3.5")
            )))

      assert(
        DockerRegistry.parseImageUri("lightbend-docker.registry.bintray.io/conductr/oci-in-docker") ==
          Success(
            Image(
              "lightbend-docker.registry.bintray.io",
              "conductr",
              "oci-in-docker",
              "latest",
              Some("lightbend-docker.registry.bintray.io"),
              Some("conductr"),
              "oci-in-docker",
              None
            )))

      assert(
        DockerRegistry.parseImageUri("lightbend-docker.registry.bintray.io/conductr/oci-in-docker:0.1") ==
          Success(
            Image(
              "lightbend-docker.registry.bintray.io",
              "conductr",
              "oci-in-docker",
              "0.1",
              Some("lightbend-docker.registry.bintray.io"),
              Some("conductr"),
              "oci-in-docker",
              Some("0.1")
            )))

      assert(DockerRegistry.parseImageUri("").isFailure)
      assert(DockerRegistry.parseImageUri("test:").isFailure)
      assert(DockerRegistry.parseImageUri(":").isFailure)
    }
  }
}
