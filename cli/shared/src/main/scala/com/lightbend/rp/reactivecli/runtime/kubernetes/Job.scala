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
import com.lightbend.rp.reactivecli.annotations._
import com.lightbend.rp.reactivecli.argparse._
import scala.collection.immutable.Seq
import scalaz._

import Argonaut._
import PodTemplate._
import Scalaz._

object Job {
  /**
   * Builds [[Job]] resource.
   */
  def generate(
    annotations: Annotations,
    apiVersion: String,
    application: Option[String],
    imageName: String,
    imagePullPolicy: ImagePullPolicy.Value,
    noOfReplicas: Int,
    externalServices: Map[String, Seq[String]],
    deploymentType: DeploymentType,
    jqExpression: Option[String],
    akkaClusterJoinExisting: Boolean): ValidationNel[String, Job] =

    (annotations.applicationValidation(application) |@| annotations.appNameValidation |@| annotations.versionValidation) { (applicationArgs, rawAppName, version) =>
      val appName = serviceName(rawAppName)
      val appNameVersion = serviceName(s"$appName$VersionSeparator$version")

      val labels = Map(
        "appName" -> appName,
        "appNameVersion" -> appNameVersion) ++ annotations.akkaClusterBootstrapSystemName.fold(Map.empty[String, String])(system => Map("actorSystemName" -> system))

      val podTemplate =
        PodTemplate.generate(
          annotations,
          apiVersion,
          application,
          imageName,
          imagePullPolicy,
          noOfReplicas,
          RestartPolicy.OnFailure,
          externalServices,
          deploymentType,
          akkaClusterJoinExisting,
          applicationArgs,
          appName,
          appNameVersion,
          labels)

      val jobName =
        deploymentType match {
          case CanaryDeploymentType => appNameVersion
          case BlueGreenDeploymentType => appNameVersion
          case RollingDeploymentType => appName
        }

      Job(
        jobName,
        Json(
          "apiVersion" -> apiVersion.asJson,
          "kind" -> jString("Job"),
          "metadata" -> Json(
            "name" -> jobName.asJson,
            "labels" -> labels.asJson)
            .deepmerge(
              annotations.namespace.fold(jEmptyObject)(ns => Json("namespace" -> serviceName(ns).asJson))),
          "spec" -> Json(
            "template" -> podTemplate.json)),
        jqExpression)
    }
}

/**
 * Represents the generated Kubernetes job resource.
 */
case class Job(name: String, json: Json, jqExpression: Option[String]) extends GeneratedKubernetesResource {
  val resourceType = "job"
}
