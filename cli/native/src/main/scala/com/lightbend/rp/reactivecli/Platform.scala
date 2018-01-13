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

package com.lightbend.rp.reactivecli

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths }
import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }
import slogging._

object Platform extends LazyLogging {
  private implicit val ec = executionContext

  def args(supplied: Array[String]): Array[String] =
    supplied

  def deleteFile(path: String): Unit =
    Files.delete(Paths.get(path))

  def environment(): Map[String, String] =
    sys.env

  /**
   * This turns out to be necessary because of Scala Native and uTest. Even though no calls
   * in our native codebase are async (all Future.successful), when you map/flatMap
   * that operation gets run on an ExecutionContext. We provide this one to ensure
   * all of those operations are done on the same thread. Thus, when uTest does
   * a call to `Await.result(future, Duration.Inf)` the future is already completed
   * and doesn't fail.
   *
   * @return
   */
  lazy val executionContext: ExecutionContext =
    new scala.concurrent.ExecutionContext {
      def execute(runnable: Runnable) =
        try {
          runnable.run()
        } catch {

          case t: Throwable => throw t
        }

      def reportFailure(t: Throwable) = {
        Console.err.println("Failure in RunNow async execution: " + t)
        Console.err.println(t.getStackTrace.mkString("\n"))
      }
    }

  def fileExists(path: String): Boolean =
    Files.exists(Paths.get(path))

  def httpRequest(request: http.HttpRequest)(implicit settings: http.HttpSettings): Future[http.HttpResponse] =
    http.NativeHttp(request) match {
      case Failure(t) => Future.failed(t)
      case Success(r) => Future.successful(r)
    }

  def mkDirs(path: String): Unit =
    Files.createDirectories(Paths.get(path))

  def pathFor(components: String*): String =
    if (components.isEmpty)
      ""
    else
      Paths.get(components.head, components.tail: _*).toString

  def processExec(args: String*): Future[(Int, String)] =
    process.NativeProcess.exec(args: _*)

  def readFile(path: String): String =
    new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)

  def stop(): Unit = {
    http.NativeHttp.globalCleanup()
  }

  def start(): Unit = {
    LoggerConfig.factory = TerminalLoggerFactory

    http.NativeHttp.globalInit().get
  }

  def withTempDir[T](f: String => Future[T]): Future[T] = {
    val dir = Files.createTempDirectory("reactive-cli")

    def clean(): Unit = {
      Files
        .list(dir)
        .iterator()
        .asScala
        .foreach(Files.delete)

      Files.delete(dir)
    }

    try {
      val future = f(dir.toString)

      future.onComplete(_ => clean())

      future
    } catch {
      case t: Throwable =>
        clean()

        throw t
    }
  }

  def withTempFile[T](f: String => Future[T]): Future[T] = {
    val file = Files.createTempFile("reactive-cli", ".temp").toString

    def clean(): Unit = {
      try {
        deleteFile(file)
      } catch {
        case t: Throwable => logger.debug(s"Failed to remove $file: ${t.getMessage}")
      }
    }

    try {
      val future = f(file)

      future.onComplete(_ => clean())

      future
    } catch {
      case t: Throwable =>
        clean()

        throw t
    }
  }

  def writeFile(path: String, data: String): Unit =
    Files.write(Paths.get(path), data.getBytes(StandardCharsets.UTF_8))
}