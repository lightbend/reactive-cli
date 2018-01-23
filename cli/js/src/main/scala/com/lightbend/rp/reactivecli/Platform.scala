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

import com.lightbend.rp.reactivecli.http.{ HttpHeaders, HttpResponse }
import io.scalajs.nodejs.child_process._
import io.scalajs.nodejs.fs.Fs
import io.scalajs.nodejs.http._
import io.scalajs.nodejs.https.Https
import io.scalajs.nodejs.os.OS
import io.scalajs.nodejs.path.Path
import io.scalajs.nodejs.process.{ argv, env }
import io.scalajs.nodejs.url.URL
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scalajs.js
import scalajs.js.URIUtils
import slogging._

import js.JSConverters._

object Platform extends LazyLogging {
  private implicit val ec = executionContext

  def args(supplied: Array[String]): Array[String] =
    argv
      .toArray
      .drop(2)

  def deleteFile(path: String): Unit =
    Fs.unlinkSync(path)

  def environment(): Map[String, String] =
    env.toMap

  def executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def fileExists(path: String): Boolean =
    Fs.existsSync(path)

  def httpRequest(request: http.HttpRequest)(implicit settings: http.HttpSettings): Future[http.HttpResponse] = {
    val url = URL.parse(request.requestUrl)

    val options = js.Dynamic.literal(
      method = request.requestMethod,
      hostname = url.hostname,
      port = if (url.port == null) null else url.port.get.toInt,
      path = url.path,
      protocol = url.protocol,
      headers = request.requestHeaders.headers.toJSDictionary.asInstanceOf[js.Object])

    settings
      .tlsCacertsPath
      .map(readFile)
      .foreach(options.ca= _)

    settings
      .tlsCertPath
      .map(readFile)
      .foreach(options.cert= _)

    settings
      .tlsKeyPath
      .map(readFile)
      .foreach(options.key= _)

    val promise = Promise[http.HttpResponse]

    val handler = { (resp: ServerResponse) =>
      var data: String = null

      resp.setEncoding("utf8")

      resp.onData { d =>
        if (data == null) {
          data = ""
        }

        data += d
      }

      resp.onEnd { () =>
        promise.success(
          HttpResponse(
            resp.statusCode,
            HttpHeaders(resp.headers.toMap),
            Option(data)))
      }
    }

    if (options.protocol.asInstanceOf[String].contains("https:"))
      Https.get(options.asInstanceOf[RequestOptions], handler)
    else
      Http.get(options.asInstanceOf[RequestOptions], handler)

    promise.future
  }

  def encodeURI(uri: String): String = URIUtils.encodeURI(uri)

  def mkDirs(path: String): Unit =
    Fs.mkdirSync(path)

  def pathFor(components: String*): String =
    if (components.isEmpty)
      ""
    else
      Path.join(components.head, components.tail: _*)

  def processExec(args: String*): Future[(Int, String)] = {
    if (args.isEmpty) {
      Future.successful(1 -> "")
    } else {
      val result = Promise[(Int, String)]

      var output = mutable.ArrayBuilder.make[Byte]()

      val processOptions = js.Dynamic.literal(windowsHide = false)

      val process = ChildProcess.spawn(args.head, args.tail.toJSArray, processOptions).asInstanceOf[js.Dynamic]

      process.stdout.on("data", { (data: js.Array[Byte]) =>
        output ++= data
      })

      process.stderr.on("data", { (data: js.Array[Byte]) =>
        output ++= data
      })

      process.on("exit", { (code: Int, signal: Int) =>
        result.success((code, new String(output.result(), StandardCharsets.UTF_8)))
      })

      process.on("error", { () =>
        // @FIXME log the error?

        result.success(127 -> "")
      })

      result.future
    }
  }

  def readFile(path: String): String =
    Fs.readFileSync(path, "utf8")

  def stop(): Unit = ()

  def start(): Unit = {
    LoggerConfig.factory = PrintLoggerFactory()
  }

  def withTempDir[T](f: String => Future[T]): Future[T] = {
    val dir = Fs.mkdtempSync(pathFor(OS.tmpdir(), "reactive-cli"))

    def clean(): Unit = {
      Fs.readdirSync(dir).foreach { file =>
        Fs.unlinkSync(pathFor(dir, file))
      }

      Fs.rmdirSync(dir)
    }

    try {
      val future = f(dir)

      future.onComplete(_ => clean())

      future
    } catch {
      case t: Throwable =>
        clean()

        throw t
    }
  }

  def withTempFile[T](f: String => Future[T]): Future[T] = {
    val dir = Fs.mkdtempSync(pathFor(OS.tmpdir(), "reactive-cli"))
    val file = pathFor(dir, "file.temp")

    writeFile(file, "")

    def clean(): Unit = {
      try {
        deleteFile(file)
      } catch {
        case t: Throwable => logger.debug(s"Failed to remove $file: ${t.getMessage}")
      }

      try {
        Fs.rmdirSync(dir)
      } catch {
        case t: Throwable => logger.debug(s"Failed to remove $dir: ${t.getMessage}")
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

  def writeFile(path: String, data: String): Unit = Fs.writeFileSync(path, data)
}