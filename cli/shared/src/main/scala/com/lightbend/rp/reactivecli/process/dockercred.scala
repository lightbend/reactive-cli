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
import com.lightbend.rp.reactivecli.files._
import scala.collection.immutable.{Seq, Map}
import scala.concurrent.Future
import slogging._
import argonaut._
import Argonaut._

object dockercred extends LazyLogging {
  private def isAvailable(kind: String): Future[Boolean] = {
    exec(s"docker-credential-$kind", "list").map(_._1 == 0)
  }

  // Returns a prioritized sequence of available credential helpers
  private def chooseHelpers(): Future[Seq[String]] = {
    def step(ks: Seq[String]): Future[List[String]] =
      if (ks.isEmpty)
        Future.successful(List.empty)
      else
        isAvailable(ks.head).flatMap {
          case true => step(ks.tail).map(ks.head :: _)
          case false => step(ks.tail)
        }

    // Helper sequence here corresponds to priority
    step(Seq("gcloud", "osxkeychain", "wincred", "pass", "secretservice"))
  }

  private def getJsonField(json: Json, field: String): Option[String] =
    json.hcursor.downField(field).focus.flatMap(_.string)

  // Returns seq of pairs server -> username from a single helper
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

  // Returns seq of tuples (server, username, helper) merged from multiple helpers
  private def listAll(ks: Seq[String]) : Future[Seq[(String, String, String)]] = {
    def step(ks: Seq[String]): Future[Map[String, (String, String)]] =
      if (ks.isEmpty)
        Future.successful(Map.empty)
      else
        list(ks.head).flatMap { creds =>
          step(ks.tail).map(_ ++ creds.map(c => c._1 -> (c._2, ks.head)).toMap)
        }

    step(ks).map(m => m.to[Seq].map { cred =>
      val (server, (username, kind)) = cred
      (server, username, kind)
    })
  }

  // Returns pair username -> password
  private def get(kind: String, server: String): Future[Option[(String, String)]] = {
    withTempFile { inputFile =>
      writeFile(inputFile, server)
      for {
        (code, output) <- execWithStdinFile(Seq(s"docker-credential-$kind", "get"), Some(inputFile))
      } yield {
        if (code == 0) {
          val json = Parse.parseOption(output)
          val username = json.flatMap(getJsonField(_, "Username"))
          val password = json.flatMap(getJsonField(_, "Secret"))

          (username, password) match {
            case (Some(username), Some(password)) => Some(username -> password)
            case _ => None
          }
        } else None
      }
    }
  }

  def getCredentials(): Future[Seq[DockerCredentials]] = {
    def step(cs: Seq[(String, String, String)]): Future[List[DockerCredentials]] = {
      if (cs.isEmpty) Future.successful(List.empty)
      else {
        val (server, username, kind) = cs.head
        get(kind, server).flatMap {
          case Some((username, password)) => step(cs.tail).map { seq =>
            DockerCredentials(server, Right(username -> password)) :: seq
          }
          case None => step(cs.tail)
        }
      }
    }

    for {
      helpers <- chooseHelpers()
      creds <- listAll(helpers)
      result <- step(creds)
    } yield result
  }
}