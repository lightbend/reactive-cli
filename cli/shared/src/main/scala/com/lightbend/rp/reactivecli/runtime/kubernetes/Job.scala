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
import com.lightbend.rp.reactivecli.json.JsonTransform
import scala.collection.immutable.Seq
import scalaz._

import Argonaut._
import PodTemplate._
import Scalaz._

object Job {
  def restartPolicyValidation(restartPolicy: RestartPolicy.Value): ValidationNel[String, RestartPolicy.Value] =
    if (restartPolicy == RestartPolicy.Always)
      "Restart policy Always is not valid for Job pod controller".failureNel
    else
      restartPolicy.successNel

  /**
   * Builds [[Job]] resource.
   */
  def generate(
    annotations: Annotations,
    apiVersion: String,
    application: Option[String],
    imageName: String,
    imagePullPolicy: ImagePullPolicy.Value,
    restartPolicy: RestartPolicy.Value,
    noOfReplicas: Int,
    externalServices: Map[String, Seq[String]],
    deploymentType: DeploymentType,
    jsonTransform: JsonTransform,
    akkaClusterJoinExisting: Boolean): ValidationNel[String, Job] =

    (annotations.applicationValidation(application)
      |@| annotations.appNameValidation
      |@| annotations.versionValidation
      |@| restartPolicyValidation(restartPolicy)) { (applicationArgs, rawAppName, version, restartPolicy) =>
        val appName = serviceName(rawAppName)
        val appNameVersion = serviceName(s"$appName$VersionSeparator$version")
        val serviceResourceName =
          deploymentType match {
            case CanaryDeploymentType => appName
            case BlueGreenDeploymentType => appNameVersion
            case RollingDeploymentType => appName
          }

        val labels = Map(
          "app" -> appName,
          "appNameVersion" -> appNameVersion) ++ annotations.akkaClusterBootstrapSystemName.fold(Map(
            serviceNameLabel -> serviceResourceName))(system => Map(serviceNameLabel -> system))

        val podTemplate =
          PodTemplate.generate(
            annotations,
            apiVersion,
            application,
            imageName,
            imagePullPolicy,
            noOfReplicas,
            if (restartPolicy == RestartPolicy.Default) RestartPolicy.OnFailure else restartPolicy,
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
          jsonTransform)
      }
}

/**
 * Represents the generated Kubernetes job resource.
 */
case class Job(name: String, json: Json, jsonTransform: JsonTransform) extends GeneratedKubernetesResource {
  val resourceType = "job"
}
