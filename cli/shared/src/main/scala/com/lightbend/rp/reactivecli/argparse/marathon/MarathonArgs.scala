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

package com.lightbend.rp.reactivecli.argparse.marathon

import com.lightbend.rp.reactivecli.argparse.{ GenerateDeploymentArgs, InputArgs, TargetRuntimeArgs }
import java.io.PrintStream
import scala.collection.immutable.Seq

object MarathonArgs {
  object Output {
    /**
     * Represents user input to save generated configuration into the file specified by [[file]].
     */
    case class SaveToFile(file: String) extends Output

    /**
     * Represents user input to pipe the generated configuration into the stream specified by [[out]].
     * The generated configuration will be formatted in the format acceptable to `dcos marathon group add` command.
     */
    case class PipeToStream(out: PrintStream) extends Output
  }

  /**
   * Base trait which indicates the output required for generated marathon configuration.
   */
  sealed trait Output

  /**
   * Convenience method to set the [[MarathonArgs]] values when parsing the complete user input.
   * Refer to [[InputArgs.parser()]] for more details.
   */
  def set[T](f: (T, MarathonArgs) => MarathonArgs): (T, InputArgs) => InputArgs = { (val1: T, inputArgs: InputArgs) =>
    GenerateDeploymentArgs
      .set { (val2: T, deploymentArgs) =>
        deploymentArgs.targetRuntimeArgs match {
          case Some(v: MarathonArgs) =>
            deploymentArgs.copy(targetRuntimeArgs = Some(f(val2, v)))
          case _ => deploymentArgs
        }
      }
      .apply(val1, inputArgs)

  }
}

/**
 * Represents user input arguments required to build marathon specific configuration.
 */
case class MarathonArgs(
  instances: Int = 1,
  marathonLbHaproxyGroup: String = "external",
  marathonLbHaproxyHosts: Seq[String] = Seq.empty,
  namespace: Option[String] = None,
  output: MarathonArgs.Output = MarathonArgs.Output.PipeToStream(System.out),
  registryForcePull: Boolean = false,
  transformOutput: Option[String] = None) extends TargetRuntimeArgs