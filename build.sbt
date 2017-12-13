import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import complete.DefaultParsers._
import scala.collection.immutable.Seq
import scalariform.formatter.preferences.AlignSingleLineCaseStatements
import ReleaseTransformations._
import _root_.bintray.BintrayExt

lazy val binaryName = SettingKey[String]("binary-name")
lazy val cSource = SettingKey[File]("c-source")
lazy val build = inputKey[Seq[(BuildInfo, Seq[File])]]("build")
lazy val buildAll = TaskKey[Seq[(BuildInfo, Seq[File])]]("buildAll")
lazy val publishToBintray = TaskKey[Unit]("publishToBintray")

lazy val Names = new {
  val `httpsimple.c`     = "httpsimple.c"
  val `httpsimple.o`     = "httpsimple.o"
  val `libhttpsimple.so` = "libhttpsimple.so"
  val rp                 = "rp"
}

lazy val Versions = new {
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
    name := "reactive-cli-root",

    releaseProcess := Seq[ReleaseStep](
      inquireVersions,
      runClean,
      releaseStepCommandAndRemaining("update"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("compile:publishToBintray"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),

    build in Compile := {
      BuildInfo.initialize(baseDirectory.value)

      for {
        name <- spaceDelimited("<arg>").parsed.toVector
        result <- BuildInfo.Builds.find(_.name == name) match {
          case None =>
            streams.value.log.error(s"Unable to find build for name: $name")

            Seq.empty

          case Some(b) =>
            val stage = target.value / "stage" / b.name
            Seq(b -> b.run(baseDirectory.value, stage, version.value, streams.value.log))
        }
      } yield result
    },

    buildAll in Compile := {
      BuildInfo.initialize(baseDirectory.value)

      for {
        b <- BuildInfo.Builds
      } yield {
        val stage = target.value / "stage" / b.name

        IO.createDirectory(stage)

        b -> b.run(baseDirectory.value, stage, version.value, streams.value.log)
      }
    }.toVector,

    publishToBintray in Compile := {
      val info = (buildAll in Compile).value
      val log = streams.value.log

      for {
        (b, files) <- info
        file       <- files
      } {
        b.target match {
          case tgt @ DebBuildTarget(distributions, components, _, _) =>
            BintrayExt.publishDeb(
              file,
              distributions,
              components,
              tgt.architecture,
              version.value,
              bintrayCredentialsFile.value,
              log)

          case RpmBuildTarget(_, _, _) =>
            BintrayExt.publishRpm(file, version.value, bintrayCredentialsFile.value, log)
        }
      }
    },

    Keys.`package` in Compile := {
      val cliOut = (Keys.`package` in (cli, Compile)).value
      val libHttpSimpleOut = (Keys.`package` in (libhttpsimple, Compile)).value
      val outputDirectory = target.value / "output"

      IO.deleteFilesEmptyDirs(Seq(outputDirectory))
      IO.createDirectory(outputDirectory / "lib")
      IO.createDirectory(outputDirectory / "bin")

      IO.copyFile(cliOut, outputDirectory / "bin" / Names.rp)
      AdditionalIO.setExecutable(outputDirectory / "bin" / Names.rp)
      IO.copyFile(libHttpSimpleOut, outputDirectory / "lib" / Names.`libhttpsimple.so`)

      (outputDirectory / Names.rp).setExecutable(true)

      streams.value.log.info(s"Created files in $outputDirectory")

      outputDirectory
    }
  )

lazy val libhttpsimple = project
  .in(file("libhttpsimple"))
  .settings(commonSettings)
  .settings(
    Keys.`package` in Compile := {
      (compile in Compile).value

      target.value / Names.`libhttpsimple.so`
    },

    compile in Compile := {
      val result = (compile in Compile).value
      val sources = (cSource in Compile).value
      val output = (target in Compile).value

      streams.value.log.info("Compiling C sources")

      val gccCode1 =
        Seq("gcc", "-c", "-fPIC", "-o", (output / Names.`httpsimple.o`).toString, (sources / Names.`httpsimple.c`).toString).!

      assert(gccCode1 == 0, s"gcc exited with $gccCode1")

      val gccCode2 =
        Seq("gcc", "-shared", "-fPIC", "-lcurl", "-o", (output / Names.`libhttpsimple.so`).toString, (output / Names.`httpsimple.o`).toString).!

      assert(gccCode2 == 0, s"gcc exited with $gccCode2")

      result
    }
  )

lazy val `libhttpsimple-bindings` = project
  .in(file("libhttpsimple-bindings"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
  .dependsOn(libhttpsimple)
  .settings(commonSettings)
  .settings(test in Compile := ())

lazy val cli = project
  .in(file("cli"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
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
    name := "reactive-cli",
    nativeMode := Properties.nativeMode,
    Keys.`package` in Compile := (nativeLink in Compile).value
  )
