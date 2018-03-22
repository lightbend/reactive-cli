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

package com.lightbend.rp.reactivecli.runtime

import argonaut._
import com.lightbend.rp.reactivecli.annotations.{ Annotations, Module }
import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.marathon._
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.Config
import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.process.jq
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scalaz._

import Argonaut._
import Scalaz._

package object marathon {
  def generateConfiguration(dockerImagesConfigs: Seq[(String, Config)], generateDeploymentArgs: GenerateDeploymentArgs, marathonArgs: MarathonArgs): Future[ValidationNel[String, GeneratedMarathonConfiguration]] =
    for {
      jqAvailable <- jq.available
    } yield {
      val marathonEntriesValidation =
        dockerImagesConfigs
          .map {
            case (_, config) =>
              val annotations = Annotations(
                config.config.Labels.getOrElse(Map.empty),
                generateDeploymentArgs)

              if (annotations.modules.contains(Module.AkkaClusterBootstrapping) && !generateDeploymentArgs.akkaClusterSkipValidation && marathonArgs.instances < AkkaClusterMinimumReplicas && !generateDeploymentArgs.akkaClusterJoinExisting)
                s"Akka Cluster Bootstrapping is enabled so you must specify `--pod-controller-replicas 2` (or greater), or provide `--akka-cluster-join-existing` to only join already formed clusters".failureNel
              else
                jEmptyObject.successNel[String]
          }
          .foldLeft(Seq.empty[Json].successNel[String]) {
            case (acc, v) => (acc |@| v)(_ :+ _)
          }

      val validateJq =
        if (marathonArgs.transformOutput.nonEmpty && !jqAvailable)
          "Resources cannot be translated because jq is not installed".failureNel
        else
          ().successNel[String]

      (marathonEntriesValidation |@| validateJq) { (marathonEntries, _) =>
        GeneratedMarathonConfiguration("", "", Future.successful(jEmptyObject))
      }
    }

  def outputConfiguration(config: GeneratedMarathonConfiguration, output: MarathonArgs.Output): Future[Unit] =
    config.payload.map { json =>
      val data = json.spaces4

      output match {
        case MarathonArgs.Output.PipeToStream(out) =>
          out.println(data)
        case MarathonArgs.Output.SaveToFile(path) =>
          mkDirs(parentFor(path))

          if (fileExists(path)) {
            deleteFile(path)
          }

          writeFile(path, data)
      }
    }
}
