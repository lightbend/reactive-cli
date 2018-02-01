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

package com.lightbend.rp.reactivecli.process

import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.DockerCredentials
import scala.collection.immutable.Seq
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import slogging._
import argonaut._
import Argonaut._

object dockercred extends LazyLogging {

  private def isAvailable(kind: String): Future[Boolean] = {
    exec(s"docker-credential-$kind", "list").map(_._1 == 0)
  }

  def choseKind(): Future[String] = {
    val kinds = Seq("osxkeychain", "wincred", "pass", "secretservice")
    optionToFuture(kinds.find(kind => Await.result(isAvailable(kind), Duration.Inf)), "No docker credential helpers found")
  }

  private def getJsonField(json: Json, field: String): Option[String] =
    json.hcursor.downField(field).focus.flatMap(_.string)

  // Returns seq of pairs server -> username
  def list(kind: String): Future[Seq[(String, String)]] = {
    for {
      (code, output) <- exec(s"docker-credential-$kind", "list")
    } yield {
      if (code == 0) {
        val json = Parse.parseOption(output)
        json.flatMap(_.hcursor.fields).get.flatMap(field => {
          json.flatMap(getJsonField(_, field)).flatMap(name => Some(field -> name))
        })
      } else {
        logger.debug(s"docker-credential-$kind exited with code $code")
        Seq.empty
      }
    }
  }

  // Returns pair username -> password
  def get(kind: String, server: String): Future[Option[(String, String)]] = {
    for {
      (code, output) <- exec("echo", s"$server", "|", s"docker-credential-$kind", "get")
    } yield {
      if(code == 0) {
        val json = Parse.parseOption(output)
        val username = json.flatMap(getJsonField(_, "Username"))
        val password = json.flatMap(getJsonField(_, "Secret"))

        (username, password) match {
          case (Some(username), Some(password)) => Some(username -> password)
          case _ => None
        }
      }
      else None
    }
  }

  def getCredentials(): Seq[DockerCredentials] = {
    Await.result(for {
      kind <- choseKind()
      lst <- list(kind)
    } yield {
      lst.flatMap({case (server, username) => {
        Await.result(for {
          maybeCreds <- get(kind, server)
        } yield {
          maybeCreds.flatMap(cred => {
            val (username, password) = cred
            Some(DockerCredentials(server, username, password, ""))
          })
        }, Duration.Inf)
      }})
    }, Duration.Inf)
  }
}