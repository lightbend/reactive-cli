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

import utest._

object DeploymentTest extends TestSuite {
  import Deployment._

  val tests = this{
    "apiVersion" - {
      "should return apps/v1beta2 for Kubernetes >= 1.8" - {
        assert(apiVersion(KubernetesVersion(2, 0)) == "apps/v1beta2")
        assert(apiVersion(KubernetesVersion(1, 8)) == "apps/v1beta2")
      }

      "should return apps/v1beta1 for Kubernetes < 1.8" - {
        assert(apiVersion(KubernetesVersion(1, 7)) == "apps/v1beta1")
        assert(apiVersion(KubernetesVersion(1, 6)) == "apps/v1beta1")
        assert(apiVersion(KubernetesVersion(1, 5)) == "apps/v1beta1")
      }
    }
  }
}
