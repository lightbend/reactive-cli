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

import java.util.NoSuchElementException
import com.lightbend.rp.reactivecli.concurrent._
import com.lightbend.rp.reactivecli.docker.DockerCredentials
import scala.collection.immutable.Seq
import scala.concurrent.Future
import slogging._
import argonaut._
import Argonaut._

object dockercred extends LazyLogging {
  private def isAvailable(kind: String): Future[Boolean] = {
    exec(s"docker-credential-$kind", "list").map(_._1 == 0)
  }

  private def chooseKind(): Future[String] = {
    def step(ks: Seq[String]): Future[String] =
      if (ks.isEmpty)
        Future.failed(new NoSuchElementException("No docker credential helper found"))
      else
        isAvailable(ks.head).flatMap {
          case true => Future.successful(ks.head)
          case false => step(ks.tail)
        }

    step(Seq("osxkeychain", "wincred", "pass", "secretservice"))
  }

  private def getJsonField(json: Json, field: String): Option[String] =
    json.hcursor.downField(field).focus.flatMap(_.string)

  // Returns seq of pairs server -> username
  private def list(kind: String): Future[Seq[(String, String)]] = {
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
  private def get(kind: String, server: String): Future[Option[(String, String)]] = {
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

  def getCredentials(): Future[Seq[DockerCredentials]] = {
    def step(kind: String, cs: Seq[(String, String)]): Future[List[DockerCredentials]] = {
      if (cs.isEmpty) Future.successful(List.empty)
      else {
        val (server, username) = cs.head
        get(kind, server).flatMap {
          case Some((username, password)) => step(kind, cs.tail).map { seq =>
            DockerCredentials(server, username, password, "") :: seq
          }
          case None => step(kind, cs.tail)
        }
      }
    }

    for {
      kind <- chooseKind()
      creds <- list(kind)
      result <- step(kind, creds)
    } yield result
  }
}