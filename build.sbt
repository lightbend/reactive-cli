import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.packager.linux.{LinuxPackageMapping, LinuxSymlink}
import scala.collection.immutable.Seq
import scalariform.formatter.preferences.AlignSingleLineCaseStatements
import ReleaseTransformations._
import _root_.bintray.BintrayExt

lazy val binaryName = SettingKey[String]("binary-name")
lazy val cSource = SettingKey[File]("c-source")

lazy val Names = new {
  val `httpsimple.c` = "httpsimple.c"
  val `httpsimple.o` = "httpsimple.o"
  val `libhttpsimple.so` = "libhttpsimple.so"
}

lazy val Properties = new {
  val nativeMode = System.getProperty("build.nativeMode", "debug")
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

  packageSummary in Linux := "Tooling for the Lightbend Reactive Platform",
  maintainer in Linux := "Lightbend <info@lightbend.com>",

  packageArchitecture in Rpm := "x86_64",
  rpmLicense := Some("Apache v2"),
  rpmVendor := "com.lightbend.rp",
  rpmUrl := Some("https://github.com/lightbend/reactive-cli"),

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

  cSource in Compile := sourceDirectory.value / "main" / "c",

  resolvers +=
    Resolver.file("Mounted", file( Path.userHome.absolutePath + "/.ivy2/mounted"))(Resolver.ivyStylePatterns)
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
      checkSnapshotDependencies,              // : ReleaseStep
      inquireVersions,                        // : ReleaseStep
      runClean,                               // : ReleaseStep
      releaseStepCommandAndRemaining("""set nativeMode in cli := "release""""),
      runTest,                                // : ReleaseStep
      setReleaseVersion,                      // : ReleaseStep
      commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
      tagRelease,                             // : ReleaseStep
      releaseStepCommandAndRemaining("debian:packageBin"),
      //releaseStepCommandAndRemaining("rpm:packageBin"),
      //publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
      setNextVersion,                         // : ReleaseStep
      commitNextVersion//,                      // : ReleaseStep
      //pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
    )
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

lazy val cli = project
  .in(file("cli"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin, JavaAppPackaging, RpmPlugin)
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

    packageName in Linux := "reactive-cli",
    packageName in Rpm := "reactive-cli",

    binaryName := "rp",

    nativeLink in Compile := {
      val out = (nativeLink in Compile).value
      val dest = out.getParentFile / binaryName.value
      IO.move(out, dest)
      dest
    },

    linuxPackageSymlinks += {
      val pkgName = (packageName in Linux).value
      LinuxSymlink(s"/usr/lib/${Names.`libhttpsimple.so`}", s"${defaultLinuxInstallLocation.value}/$pkgName/lib/${Names.`libhttpsimple.so`}")
    },
    nativeMode := Properties.nativeMode,

    mappings in Universal := Seq(
      (nativeLink in Compile).value -> s"bin/${binaryName.value}",
      ((target in (libhttpsimple, Compile)).value / Names.`libhttpsimple.so`) -> s"lib/${Names.`libhttpsimple.so`}"
    ),

    debianPackageDependencies in Debian := Seq(
      "libcurl3",
      "libre2-1v5",
      "libunwind8"
    ),

    TaskKey[Unit]("publishToBintray") := {
      BintrayExt.publishDeb(
        (packageBin in Debian).value,
        version.value,
        bintrayCredentialsFile.value,
        streams.value.log)

      BintrayExt.publishRpm(
        (packageBin in Rpm).value,
        version.value,
        bintrayCredentialsFile.value,
        streams.value.log)
    }
  )
