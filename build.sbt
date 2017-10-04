import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scala.collection.immutable.Seq
import scalariform.formatter.preferences.AlignSingleLineCaseStatements

val binaryName = SettingKey[String]("binary-name")

val Versions = new {
  val argonaut = "6.3-SNAPSHOT"
  val scala    = "2.11.11"
  val scopt    = "3.7.0"
  val utest    = "0.5.3"
}

lazy val commonSettings = Seq(
  organization := "com.lightbend.rp",

  organizationName := "Lightbend, Inc.",

  startYear := Some(2017),

  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),

  maintainer := "info@lightbend.com",

  rpmVendor := organizationName.value,

  scalaVersion := Versions.scala,

  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "utest" % Versions.utest % "test"
  ),

  ScalariformKeys.preferences :=
    ScalariformKeys.preferences.value
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100),

  nativeLinkStubs := true,

  testFrameworks += new TestFramework("utest.runner.Framework")
)

lazy val root = project
  .in(file("."))
  .aggregate(
    `libhttpsimple-bindings`,
    `cli`
  )
  .settings(
    name := "reactive-cli"
  )

lazy val `libhttpsimple-bindings` = project
  .in(file("libhttpsimple-bindings"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
  .settings(commonSettings)

lazy val cli = project
  .in(file("cli"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin, JavaAppPackaging)
  .dependsOn(`libhttpsimple-bindings`)
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "com.github.scopt"  %%% "scopt"    % Versions.scopt,
      "io.argonaut"       %%% "argonaut" % Versions.argonaut
    )
  ))
  .settings(
    binaryName := "rp",
    packageSummary := "Tools for managing and deploying Lightbend Reactive Platform applications",
    nativeLink in Compile := {
      val out = (nativeLink in Compile).value
      val dest = out.getParentFile / binaryName.value
      IO.move(out, dest)
      dest
    },
    packageName in Linux := "reactive-cli",
    mappings in Universal := Seq(
      (nativeLink in Compile).value -> s"bin/${binaryName.value}"
    )
  )
