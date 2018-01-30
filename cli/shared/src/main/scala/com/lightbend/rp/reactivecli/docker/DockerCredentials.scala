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

import com.lightbend.rp.reactivecli.files._
import com.lightbend.rp.reactivecli.process._
import scala.collection.immutable.Seq
import scala.concurrent._
import argonaut._
import Argonaut._

case class DockerCredentials(registry: String, username: String, password: String, token: String)

/**
 * Parses a file for credentials
 */
object DockerCredentials {
  private val Registry = "registry"
  private val Username = "username"
  private val Password = "password"

  def get(credsFilePath: Option[String], configFilePath: Option[String]): Seq[DockerCredentials] = {
    val fromConfig = configFilePath.map(parseDockerConfig)
    val fromCreds = credsFilePath.map(parseCredsFile)
    val fromHelpers = getHelperCredentials()
    // TODO: Solve duplicates here
    fromConfig.getOrElse(Seq.empty) ++ fromCreds.getOrElse(Seq.empty) ++ fromHelpers
  }

  def getHelperCredentials(): Seq[DockerCredentials] = {
    val helpers = Seq(
      "docker-credential-osxkeychan",
      "docker-credential-wincred",
      "docker-credential-pass",
      "docker-credential-secretservice")

    // Find first available helper executable
    helpers.find(helper => {
      val futureCode = for {
        (code, output) <- exec(helper)
      } yield {
        if (code == 0) true
        else false
      }

      Await.result(futureCode)
    })

    // TODO: Put back code to parse "{helper list}" & "{helper get}" outputs

    Seq.empty
  }

  def parseDockerConfig(configFilePath: String): Seq[DockerCredentials] =
    decodeConfig(readFile(configFilePath))

  def parseCredsFile(credsFilePath: String): Seq[DockerCredentials] =
    decodeCreds(readFile(credsFilePath))

  /**
    * Decodes ~/.docker/config.json, where authentication tokens may be stored.
    * Example:
    *  {
    *     "auths": {
		*       "https://index.docker.io/v1/": {
		*          "auth": "0123abcdef="
    *       }
    *     },
	  *     "HttpHeaders": {
		*       "User-Agent": "Docker-Client/17.12.0-ce (linux)"
	  *     }
    *  }
   */
  def decodeConfig(content: String) : Seq[DockerCredentials] = {
    val auths = Parse.parseOption(content).flatMap(_.hcursor.downField("auths").focus)
    val fields = auths.flatMap(_.hcursor.fields)
    if (auths.isDefined && fields.isDefined) {
      fields.get.flatMap(field => {
        val auth = auths.flatMap(_.hcursor.downField(field).downField("auth").focus)
        auth match {
          case Some(token) if token.isString => Some(DockerCredentials(field, "", "", token.string.get))
          case _ => None
        }
      })
    }
    else Seq.empty
  }


  /**
   * Decodes a Docker credential file. This is a simplistic format with a number of
   * key = value pairs (white-space trimmed) separated by "\n" characters.
   *
   * Recognized keys: registry, username, password
   *
   * Example file:
   *
   * registry = lightbend-docker-registry.bintray.io
   * username = hello
   * password = there
   *
   * registry = registry.hub.docker.com
   * username = foo
   * password = bar
   *
   * yields
   *
   * Seq(
   *   DockerCredentials("lightbend-docker-registry.bintray.io", "hello", "there"),
   *   DockerCredentials("registry.hub.docker.com", "foo", "bar"))
   */
  def decodeCreds(content: String): Seq[DockerCredentials] =
    lines(content)
      .foldLeft(List.empty[DockerCredentials]) {
        case (accum, next) =>
          val modify =
            accum.nonEmpty && (
              accum.head.registry.isEmpty ||
              accum.head.username.isEmpty ||
              accum.head.password.isEmpty)

          parseLine(next) match {
            case (Registry, registry) =>
              if (modify)
                accum.head.copy(registry = registry) :: accum.tail
              else
                DockerCredentials(registry, "", "", "") :: accum

            case (Username, username) =>
              if (modify)
                accum.head.copy(username = username) :: accum.tail
              else
                DockerCredentials("", username, "", "") :: accum

            case (Password, password) =>
              if (modify)
                accum.head.copy(password = password) :: accum.tail
              else
                DockerCredentials("", "", password, "") :: accum

            case _ =>
              accum
          }
      }
      .reverse

  private[docker] def parseLine(line: String): (String, String) = {
    val parts = line.split("=", 2).lift

    parts(0).map(_.trim).getOrElse("") -> parts(1).map(_.trim).getOrElse("")
  }

  private[docker] def lines(content: String): Array[String] =
    content
      .replaceAllLiterally("\r\n", "\n")
      .replaceAllLiterally("\r", "")
      .split('\n')
}
