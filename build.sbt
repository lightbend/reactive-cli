import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scala.collection.immutable.Seq
import scalariform.formatter.preferences.AlignSingleLineCaseStatements

val binaryName = SettingKey[String]("binary-name")
val cSource = SettingKey[File]("c-source")

val Versions = new {
  val argonaut = "6.3-SNAPSHOT"
  val scala    = "2.11.11"
  val scalaz   = "7.2.16"
  val scopt    = "3.7.0"
  val slogging = "0.6.0"
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

  testFrameworks += new TestFramework("utest.runner.Framework"),

  cSource in Compile := sourceDirectory.value / "main" / "c"
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

lazy val libhttpsimple = project
  .in(file("libhttpsimple"))
  .settings(commonSettings)
  .settings(
    compile in Compile := {
      val result = (compile in Compile).value
      val sources = (cSource in Compile).value
      val output = (target in Compile).value

      val gccCode1 =
        Seq("gcc", "-c", "-fPIC", "-o", (output / "httpsimple.o").toString, (sources / "httpsimple.c").toString).!

      assert(gccCode1 == 0, s"gcc exited with $gccCode1")

      val gccCode2 =
        Seq("gcc", "-shared", "-fPIC", "-lcurl", "-o", (output / "libhttpsimple.so").toString, (output / "httpsimple.o").toString).!

      assert(gccCode2 == 0, s"gcc exited with $gccCode2")

      result
    }
  )

lazy val `libhttpsimple-bindings` = project
  .in(file("libhttpsimple-bindings"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
  .dependsOn(libhttpsimple)
  .settings(commonSettings)

lazy val cli = project
  .in(file("cli"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin, JavaAppPackaging)
  .dependsOn(`libhttpsimple-bindings`)
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "com.github.scopt"  %%% "scopt"       % Versions.scopt,
      "io.argonaut"       %%% "argonaut"    % Versions.argonaut,
      "biz.enef"          %%% "slogging"    % Versions.slogging,
      "org.scalaz"        %%% "scalaz-core" % Versions.scalaz
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
