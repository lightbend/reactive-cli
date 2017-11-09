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

package com.lightbend.rp.reactivecli.argparse.kubernetes

import com.lightbend.rp.reactivecli.argparse.InputArgs
import com.lightbend.rp.reactivecli.runtime.kubernetes.Deployment

object DeploymentArgs {
  /**
   * Convenience method to set the [[DeploymentArgs]] values when parsing the complete user input.
   * Refer to [[InputArgs.parser()]] for more details.
   */
  def set[T](f: (T, DeploymentArgs) => DeploymentArgs): (T, InputArgs) => InputArgs = { (val1: T, inputArgs: InputArgs) =>
    KubernetesArgs
      .set { (val2: T, kubernetesArgs) =>
        kubernetesArgs.copy(
          deploymentArgs = f(val2, kubernetesArgs.deploymentArgs))
      }
      .apply(val1, inputArgs)
  }
}

/**
 * Represents user input arguments required to build Kubernetes Deployment resource.
 */
case class DeploymentArgs(
  numberOfReplicas: Int = KubernetesArgs.DefaultNumberOfReplicas,
  imagePullPolicy: Deployment.ImagePullPolicy.Value = KubernetesArgs.DefaultImagePullPolicy)
