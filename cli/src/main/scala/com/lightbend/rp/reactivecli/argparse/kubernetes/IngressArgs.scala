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

object IngressArgs {

  object IngressNgnixArgs {

    /**
     * Convenience method to set the [[IngressNgnixArgs]] values when parsing the complete user input.
     * Refer to [[InputArgs.parser()]] for more details.
     */
    def set[T](f: (T, IngressNgnixArgs) => IngressNgnixArgs): (T, InputArgs) => InputArgs = { (val1: T, inputArgs: InputArgs) =>
      KubernetesArgs
        .set { (val2: T, kubernetesArgs) =>
          kubernetesArgs.ingressArgs match {
            case Some(ingressNginxArgs: IngressNgnixArgs) =>
              kubernetesArgs.copy(
                ingressArgs = Some(f(val2, ingressNginxArgs)))
            case _ => kubernetesArgs
          }
        }
        .apply(val1, inputArgs)
    }
  }

  /**
   * Represents user input arguments required to build Kubernetes Ingress resource using Nginx.
   */
  case class IngressNgnixArgs(tlsSecretName: Option[String] = None, sslRedirect: Boolean = false) extends IngressArgs

  /**
   * Represents user input arguments required to build Kubernetes Deployment resource using Istio.
   */
  case object IngressIstioArgs extends IngressArgs
}

/**
 * Base trait which represents user input arguments required to build Kubernetes Ingress resource.
 */
sealed trait IngressArgs

