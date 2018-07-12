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
lazy val buildDockerImage = inputKey[Seq[String]]("buildDockerImage")
lazy val buildAll = TaskKey[Seq[(BuildInfo, Seq[File])]]("buildAll")
lazy val buildAllDockerImages = TaskKey[Seq[String]]("buildAllDockerImages")
lazy val publishToBintray = TaskKey[Unit]("publishToBintray")

// FIXME shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{ crossProject, CrossType }

lazy val Names = new {
  val rp = "rp"
}

lazy val Versions = new {
  val argonaut  = "6.2.2"
  val fastparse = "1.0.0"
  val nodejs    = "0.4.2"
  val scala     = "2.11.12"
  val scalaz    = "7.2.16"
  val scopt     = "3.7.0"
  val slogging  = "0.6.0"
  val utest     = "0.5.3"
}

lazy val Platform = new {
  val isWindows =
    sys
      .props
      .get("os.name")
      .map(_.toLowerCase)
      .exists(_.contains("win"))
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
    cliJs, cliNative
  )
  .settings(
    name := "reactive-cli-root",

    TaskKey[Unit]("ensureRelease") := {
      if (Properties.nativeMode != "release") {
        sys.error("To release, you must launch SBT with -Dbuild.nativeMode=release")
      }
    },

    releaseProcess := Seq[ReleaseStep](
      inquireVersions,
      runClean,
      releaseStepCommand("ensureRelease"),
      releaseStepCommand("update"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommand("compile:publishToBintray"),
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

    buildDockerImage in Compile := {
      for {
        name <- spaceDelimited("<arg>").parsed.toVector
        result <- BuildInfo.Builds.find(_.name == name) match {
          case None =>
            streams.value.log.error(s"Unable to find build for name: $name")

            Seq.empty

          case Some(b) =>
            val stage = target.value / "stage" / b.name
            val tag = b.build(stage)

            streams.value.log.warn("The build has been completed but the image has not been published. To publish:")
            streams.value.log.warn(s"""docker push "$tag"""")

            Seq(tag)
        }
      } yield result
    },

    buildAllDockerImages in Compile := {
      val tags =
        for {
          b <- BuildInfo.Builds
        } yield {
          val stage = target.value / "stage" / b.name

          IO.createDirectory(stage)

          b.build(stage)
        }

      streams.value.log.warn("The build has been completed but the image has not been published. To publish:")

      tags.foreach { tag =>
        streams.value.log.warn(s"""docker push "$tag"""")
      }

      tags
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

          case TarGzSelfContainedExecutableBuildTarget(_) =>
            BintrayExt.publishTarGz(file, version.value, bintrayCredentialsFile.value, log)
        }
      }
    }
  )

lazy val cli = crossProject(JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("cli"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "com.github.scopt"  %%% "scopt"       % Versions.scopt,
      "io.argonaut"       %%% "argonaut"    % Versions.argonaut,
      "biz.enef"          %%% "slogging"    % Versions.slogging,
      "org.scalaz"        %%% "scalaz-core" % Versions.scalaz,
      "com.lihaoyi"       %%% "fastparse"   % Versions.fastparse
    )
  ))
  .settings(
    name := "reactive-cli",
    sourceGenerators in Compile += Def.task {
      val versionFile = (sourceManaged in Compile).value / "ProgramVersion.scala"

      val versionSource =
        """|package com.lightbend.rp.reactivecli
           |
           |object ProgramVersion {
           |  val current = "%s"
           |}
           |"""
          .stripMargin
          .format(version.value)
          .replaceAllLiterally("\n", System.lineSeparator)

      IO.write(versionFile, versionSource)

      Seq(versionFile)
    }
  )
  .nativeSettings(
    nativeMode := Properties.nativeMode,

    nativeGC := "none",

    nativeLinkingOptions := {
      val dynamicLinkerOptions =
        Properties
          .dynamicLinker
          .toVector
          .map(dl => s"-Wl,--dynamic-linker=$dl")

      dynamicLinkerOptions ++ Seq(
        "-lcurl"
      ) ++ sys.props.get("nativeLinkingOptions").fold(Seq.empty[String])(_.split(" ").toVector)
    },

    Keys.`package` in Compile := {
      val cliOut = (nativeLink in Compile).value
      val outputDirectory = target.value / "output"

      IO.deleteFilesEmptyDirs(Seq(outputDirectory))
      IO.createDirectory(outputDirectory / "lib")
      IO.createDirectory(outputDirectory / "bin")

      IO.copyFile(cliOut, outputDirectory / "bin" / Names.rp)
      AdditionalIO.setExecutable(outputDirectory / "bin" / Names.rp)

      (outputDirectory / Names.rp).setExecutable(true)

      streams.value.log.info(s"Created files in $outputDirectory")

      outputDirectory
    }
  )
  .jsSettings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSModuleKind := ModuleKind.CommonJSModule,
    libraryDependencies ++= Seq(
      "io.scalajs" %%% "nodejs-lts" % Versions.nodejs
    ),
    Keys.`package` in Compile := {
      val output = (fullOptJS in Compile).value.data

      val entry =
        s"""|__ScalaJSEnv = {
            |  exitFunction: function(status) {
            |    process.exit(status);
            |  }
            |};
            |
            |"""
          .stripMargin
          .replaceAllLiterally("\n", System.lineSeparator)

      val targetDir = (target in Compile).value

      val entryFile = targetDir / "rp.js"

      IO.write(entryFile, entry + IO.read(output))

      entryFile
    }
  )

lazy val cliJs = cli.js

lazy val cliNative = cli.native
