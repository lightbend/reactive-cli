import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences.AlignSingleLineCaseStatements

val Versions = new {
  val argonaut = "6.3-SNAPSHOT"
  val scala = "2.11.11"
  val scopt = "3.7.0"
  val utest = "0.4.8"
}

lazy val commonSettings = Seq(
  organization := "com.lightbend.rp",

  organizationName := "Lightbend, Inc.",

  startYear := Some(2017),

  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),

  scalaVersion := Versions.scala,

  libraryDependencies ++= List(
    "com.lihaoyi" %%% "utest" % Versions.utest % "test"
  ),

  ScalariformKeys.preferences :=
    ScalariformKeys.preferences.value
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100),

  testFrameworks += new TestFramework("utest.runner.Framework")
)

lazy val root = project
  .in(file("."))
  .aggregate(
    `libhttpsimple-bindings`,
    `k8s-cli`
  )
  .settings(
    name := "reactive-cli"
  )

lazy val `libhttpsimple-bindings` = project
  .in(file("libhttpsimple-bindings"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
  .settings(commonSettings)

lazy val `k8s-cli` = project
  .in(file("k8s-cli"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= List(
      "com.github.scopt"  %%% "scopt"    % Versions.scopt,
      "io.argonaut"       %%% "argonaut" % Versions.argonaut
    )
  ))
  .dependsOn(`libhttpsimple-bindings`)
