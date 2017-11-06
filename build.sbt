import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.packager.linux.{LinuxPackageMapping, LinuxSymlink}
import scala.collection.immutable.Seq
import scalariform.formatter.preferences.AlignSingleLineCaseStatements
import ReleaseTransformations._

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

  maintainer := "info@lightbend.com",

  scmInfo := Some(ScmInfo(url("https://github.com/lightbend/reactive-cli"), "git@github.com:lightbend/reactive-cli.git")),

  bintrayOrganization := Some("lightbend"),

  bintrayReleaseOnPublish in ThisBuild := false,

  bintrayRepository := "deb",

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
    name := "reactive-cli",
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,              // : ReleaseStep
      inquireVersions,                        // : ReleaseStep
      runClean,                               // : ReleaseStep
      runTest,                                // : ReleaseStep
      setReleaseVersion,                      // : ReleaseStep
      commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
      tagRelease,                             // : ReleaseStep
      //publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
      setNextVersion,                         // : ReleaseStep
      commitNextVersion,                      // : ReleaseStep
      pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
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
    bintrayPackage := "reactive-cli",
    packageSummary := "Tools for managing and deploying Lightbend Reactive Platform applications",
    publishArtifact in (Compile, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,
    publishMavenStyle := false,
    //publishArtifact in (Debian, packageBin) := true,
    addArtifact(Artifact("reactive-cli", "deb", "deb;deb_distribution=wheezy;deb_component=main;deb_architecture=amd64"), packageBin in Debian),
    nativeLink in Compile := {
      val out = (nativeLink in Compile).value
      val dest = out.getParentFile / binaryName.value
      IO.move(out, dest)
      dest
    },
    packageName in Linux := "reactive-cli",
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
    )

    //,

   // publish in Debian := {
   //   println("hey")
   // }
  )
