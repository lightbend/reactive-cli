package bintray

import dispatch._, Defaults._
import sbt.{ File, Logger }
import scala.concurrent.Await
import scala.concurrent.duration._
//import Keys._

/**
 * sbt-bintray doesn't have implementations for rpm and deb uploading so
 * this implements the functionality in the most straight-forward way.
 *
 *
 */
object BintrayExt {
  def publishDeb(file: File, version: String, bintrayCredentialsFile: File, log: Logger): Unit = {
    val urlString =
      s"https://api.bintray.com/content/lightbend/deb/reactive-cli/$version/${file.getName}"

    val request = withAuth(Bintray.ensuredCredentials(bintrayCredentialsFile, log))(
      url(urlString)
        .addHeader("X-Bintray-Debian-Distribution", "stable")
        .addHeader("X-Bintray-Debian-Component", "main")
        .addHeader("X-Bintray-Debian-Architecture", "amd64") <<< file
    )

    log.info(s"Uploading ${file.getName} to $urlString")

    val response = Await.result(Http(request), Duration.Inf)

    val responseText = s"[${response.getStatusCode} ${response.getStatusText}] ${response.getResponseBody}"

    if (response.getStatusCode >= 200 && response.getStatusCode <= 299)
      log.info(responseText)
    else
      sys.error(responseText)
  }

  def publishRpm(file: File, version: String, bintrayCredentialsFile: File, log: Logger): Unit = {
    val urlString =
      s"https://api.bintray.com/content/lightbend/rpm/reactive-cli/$version/${file.getName}"

    val request = withAuth(Bintray.ensuredCredentials(bintrayCredentialsFile, log))(
      url(urlString)
        .addHeader("X-Bintray-Debian-Distribution", "stable")
        .addHeader("X-Bintray-Debian-Component", "main")
        .addHeader("X-Bintray-Debian-Architecture", "amd64") <<< file
    )

    log.info(s"Uploading ${file.getName} to $urlString")

    val response = Await.result(Http(request), Duration.Inf)

    val responseText = s"[${response.getStatusCode} ${response.getStatusText}] ${response.getResponseBody}"

    if (response.getStatusCode >= 200 && response.getStatusCode <= 299)
      log.info(responseText)
    else
      sys.error(responseText)
  }

  private def withAuth(credentials: Option[BintrayCredentials])(request: Req) =
    credentials.fold(request)(c => request.as_!(c.user, c.password))
}
