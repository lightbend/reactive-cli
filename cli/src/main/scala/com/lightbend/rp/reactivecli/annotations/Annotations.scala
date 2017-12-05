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

package com.lightbend.rp.reactivecli.annotations

import com.lightbend.rp.reactivecli.argparse.GenerateDeploymentArgs
import com.lightbend.rp.reactivecli.argparse.kubernetes.KubernetesArgs
import scala.collection.immutable.Seq
import scala.util.Try
import scala.util.matching.Regex
import scalaz._

import Scalaz._

case class Annotations(
  namespace: Option[String],
  appName: Option[String],
  appType: Option[String],
  diskSpace: Option[Long],
  memory: Option[Long],
  nrOfCpus: Option[Double],
  endpoints: Map[String, Endpoint],
  secrets: Seq[Secret],
  volumes: Map[String, Volume],
  privileged: Boolean,
  healthCheck: Option[Check],
  readinessCheck: Option[Check],
  environmentVariables: Map[String, EnvironmentVariable],
  version: Option[String],
  modules: Set[String]) {

  def appNameValidation: ValidationNel[String, String] =
    appName.fold[ValidationNel[String, String]]("Docker label \"com.lightbend.rp.app-name\" must be defined".failureNel)(_.successNel)

  def versionValidation: ValidationNel[String, String] =
    version.fold[ValidationNel[String, String]]("Docker label \"com.lightbend.rp.version\" must be defined".failureNel)(_.successNel)
}

/**
 * Parses annotations in the RP format (typically stored in Docker labels)
 *
 * Example Input:
 *
 * Map(
 *   "com.lightbend.rp.disk-space" -> "65536",
 *   "com.lightbend.rp.environment-variables.0.type -> "literal",
 *   "com.lightbend.rp.environment-variables.0.name -> "GREETING",
 *   "com.lightbend.rp.environment-variables.0.value -> "Hello!"
 * )
 *
 * Example Output:
 *
 * Annotations(
 *   diskSpace = Some(65535L),
 *   environmentVariables = Map(
 *     "GREETING" -> LiteralEnvironmentVariable("HELLO!")
 *   )
 * )
 */
object Annotations {
  def apply(labels: Map[String, String], args: GenerateDeploymentArgs): Annotations = {
    val appVersion = version(labels)
    Annotations(
      namespace = namespace(args).orElse(namespace(labels)),
      appName = appName(labels),
      appType = appType(labels),
      diskSpace = args.diskSpace.orElse(diskSpace(labels)),
      memory = args.memory.orElse(memory(labels)),
      nrOfCpus = args.nrOfCpus.orElse(nrOfCpus(labels)),
      endpoints = endpoints(selectArrayWithIndex(labels, ns("endpoints")), appVersion),
      secrets = secrets(selectArray(labels, ns("secrets"))),
      volumes = volumes(selectArray(labels, ns("volumes"))),
      privileged = privileged(labels),
      healthCheck = check(selectSubset(labels, ns("health-check"))),
      readinessCheck = check(selectSubset(labels, ns("readiness-check"))),
      environmentVariables = environmentVariables(selectArray(labels, ns("environment-variables"))) ++
        args.environmentVariables.mapValues(LiteralEnvironmentVariable.apply),
      version = appVersion,
      modules = appModules(selectSubset(labels, ns("modules"))))
  }

  private[annotations] def namespace(labels: Map[String, String]): Option[String] =
    labels
      .get(ns("namespace"))

  private[annotations] def namespace(args: GenerateDeploymentArgs): Option[String] =
    args.targetRuntimeArgs.collect {
      case KubernetesArgs(_, _, _, _, Some(namespace), _, _, _, _) => namespace
    }

  private[annotations] def appName(labels: Map[String, String]): Option[String] =
    labels
      .get(ns("app-name"))

  private[annotations] def appType(labels: Map[String, String]): Option[String] =
    labels
      .get(ns("app-type"))

  private[annotations] def appModules(modules: Map[String, String]): Set[String] = {
    val suffix = ".enabled"

    val parsed =
      for {
        (key, value) <- modules

        if key.endsWith(suffix)

        if value == "true"
      } yield key.dropRight(suffix.length)

    parsed.toSet
  }

  private[annotations] def diskSpace(labels: Map[String, String]): Option[Long] =
    labels
      .get(ns("disk-space"))
      .flatMap(decodeLong)

  private[annotations] def memory(labels: Map[String, String]): Option[Long] =
    labels
      .get(ns("memory"))
      .flatMap(decodeLong)

  private[annotations] def nrOfCpus(labels: Map[String, String]): Option[Double] =
    labels
      .get(ns("nr-of-cpus"))
      .flatMap(decodeDouble)

  private[annotations] def privileged(labels: Map[String, String]): Boolean =
    labels
      .get(ns("privileged"))
      .flatMap(decodeBoolean)
      .getOrElse(false)

  private[annotations] def version(labels: Map[String, String]): Option[String] =
    labels
      .get(ns("app-version"))

  private[annotations] def check(check: Map[String, String]): Option[Check] = {
    for {
      typ <- check.get("type")
      value <- typ match {
        case "command" =>
          val args = selectArray(check, "args").flatMap(_.values)

          if (args.isEmpty)
            None
          else
            Some(CommandCheck(args))

        case "http" =>
          for {
            intervalSeconds <- check.get("interval").flatMap(decodeInt)
            path <- check.get("path")
            port = check.get("port").flatMap(decodeInt).getOrElse(0)
            serviceName = check.getOrElse("service-name", "")

            if port != 0 || serviceName != ""
          } yield {
            val checkPort = if (port != 0) Check.PortNumber(port) else Check.ServiceName(serviceName)
            HttpCheck(checkPort, intervalSeconds, path)
          }

        case "tcp" =>
          for {
            intervalSeconds <- check.get("interval").flatMap(decodeInt)
            port = check.get("port").flatMap(decodeInt).getOrElse(0)
            serviceName = check.getOrElse("service-name", "")

            if port != 0 || serviceName != ""
          } yield {
            val checkPort = if (port != 0) Check.PortNumber(port) else Check.ServiceName(serviceName)
            TcpCheck(checkPort, intervalSeconds)
          }
      }
    } yield value
  }

  private[annotations] def secrets(secrets: Seq[Map[String, String]]): Seq[Secret] =
    for {
      entry <- secrets
      ns <- entry.get("namespace")
      name <- entry.get("name")
    } yield Secret(ns, name)

  private[annotations] def endpoints(endpoints: Seq[(Int, Map[String, String])], version: Option[String]): Map[String, Endpoint] =
    endpoints.flatMap(v => endpoint(v._2, v._1, version)).toMap

  private[annotations] def endpoint(entry: Map[String, String], index: Int, version: Option[String]): Option[(String, Endpoint)] =
    entry.get("protocol")
      .collect {
        case "http" => endpointHttp(version, entry, index)
        case "tcp" => endpointTcp(version, entry, index)
        case "udp" => endpointUdp(version, entry, index)
      }
      .flatten
      .map(v => v.name -> v)

  private[annotations] def endpointHttp(version: Option[String], entry: Map[String, String], index: Int): Option[HttpEndpoint] =
    entry.get("name").map(
      HttpEndpoint(
        index,
        _,
        entry.get("port").flatMap(decodeInt).getOrElse(0),
        httpIngress(selectArray(entry, "ingress"))))

  private[annotations] def endpointTcp(version: Option[String], entry: Map[String, String], index: Int): Option[TcpEndpoint] =
    entry.get("name").map(
      TcpEndpoint(
        index,
        _,
        entry.get("port").flatMap(decodeInt).getOrElse(0)))

  private[annotations] def endpointUdp(version: Option[String], entry: Map[String, String], index: Int): Option[UdpEndpoint] =
    entry.get("name").map(
      UdpEndpoint(
        index,
        _,
        entry.get("port").flatMap(decodeInt).getOrElse(0)))

  private[annotations] def httpIngress(ingress: Seq[Map[String, String]]): Seq[HttpIngress] =
    ingress
      .flatMap { entry =>
        for {
          typ <- entry.get("type")

          if typ == "http"

          ports = for {
            ingressPortEntry <- selectArray(entry, "ingress-ports")
            value <- ingressPortEntry.values
            port <- decodeInt(value)
          } yield port

          hosts = for {
            pathEntry <- selectArray(entry, "hosts")
            path <- pathEntry.values
          } yield path

          paths = for {
            pathEntry <- selectArray(entry, "paths")
            path <- pathEntry.values
          } yield path
        } yield HttpIngress(ports, hosts, paths)
      }

  private[annotations] def environmentVariables(variables: Seq[Map[String, String]]): Map[String, EnvironmentVariable] =
    variables
      .flatMap(entry =>
        for {
          typ <- entry.get("type")
          name <- entry.get("name")
          value <- typ match {
            case "literal" =>
              entry.get("value").map(LiteralEnvironmentVariable.apply)

            case "kubernetes.configMap" =>
              for {
                mapName <- entry.get("map-name")
                key <- entry.get("key")
              } yield kubernetes.ConfigMapEnvironmentVariable(mapName, key)

            case "kubernetes.fieldRef" =>
              entry.get("field-path").map(kubernetes.FieldRefEnvironmentVariable.apply)

            case _ =>
              // We don't expose kubernetes.SecretKeyRefEnvironmentVariable
              // as we encourage using reactive-lib instead, it is only used
              // internally

              None
          }
        } yield name -> value)
      .toMap

  private[annotations] def volumes(volumes: Seq[Map[String, String]]): Map[String, Volume] =
    volumes
      .flatMap(entry =>
        for {
          typ <- entry.get("type")
          guestPath <- entry.get("guest-path")
          value <- typ match {
            case "host-path" =>
              entry.get("path").map(HostPathVolume.apply)

            case _ =>
              None
          }
        } yield guestPath -> value)
      .toMap

  private[annotations] def decodeBoolean(s: String) =
    if (s == "true")
      Some(true)
    else if (s == "false")
      Some(false)
    else
      None

  private[annotations] def decodeDouble(s: String) = {
    // @FIXME Once https://github.com/scala-native/scala-native/pull/1047 is merged we can remove the regex guard

    if (!s.matches("^-?[0-9]+([.][0-9]*)?$"))
      None
    else
      Try(s.toDouble)
        .toOption
  }

  private[annotations] def decodeInt(s: String) =
    Try(s.toInt).toOption

  private[annotations] def decodeLong(s: String) =
    Try(s.toLong).toOption

  private[annotations] def selectArray(keys: Map[String, String], namespace: String): Seq[Map[String, String]] =
    selectArrayWithIndex(keys, namespace).map(_._2)

  private[annotations] def selectArrayWithIndex(keys: Map[String, String], namespace: String): Seq[(Int, Map[String, String])] = {
    val nsPattern = s"^${Regex.quote(namespace)}\\.([0-9]+)(\\.(.+))?$$".r

    keys
      .flatMap {
        case (key, value) =>
          val groups =
            nsPattern
              .findAllMatchIn(key)
              .toVector
              .headOption
              .map(_.subgroups)

          groups match {
            case Some(index :: _ :: keyOrNull :: Nil) =>
              Some((index.toInt, Option(keyOrNull).getOrElse(""), value))
            case _ =>
              None
          }
      }
      .groupBy(_._1)
      .toVector
      .sortBy(_._1)
      .map(v => v._1 -> v._2.map(entry => (entry._2, entry._3)).toMap)
  }

  private[annotations] def selectSubset(keys: Map[String, String], namespace: String): Map[String, String] = {
    keys.flatMap {
      case (key, value) =>
        val prefix = namespace + "."

        if (key.startsWith(prefix))
          Some(key.drop(prefix.length) -> value)
        else
          None
    }
  }

  private def ns(key: String*): String =
    (Seq("com", "lightbend", "rp") ++ key.map(_.toString.filter(_ != "."))).mkString(".")
}