import sbt._
import scala.collection.immutable.Seq

trait BuildTarget {
  // These fields are hard-coded for now but could become arguments if we generalize this build setup

  protected val buildDescription = "Tools for the Lightbend Reactive Platform"
  protected val buildLicense = "Apache 2.0"
  protected val buildPackage = "reactive-cli"
  protected val buildDebArch = "amd64"
  protected val buildDebMaintainer = "Lightbend, Inc"
  protected val buildRpmArch = "x86_64"
  protected val buildRpmSummary = "Reactive CLI"
  protected val buildRpmVendor = "Lightbend, Inc <info@lightbend.com>"

  /**
   * Should create a script at stage/command that will build the project
   * and create any appropriate packages in stage/output
   */
  def prepare(stage: File, info: BuildInfo, version: String): Unit

  protected def postBuildHook: String = ""

  protected def preBuildHook: String = ""

  protected def sbtBuildArgumentsHook: String = ""

  protected def launcherHook: String = ""

  /**
   * Creates a file at stage/build that will build the project but not package it
   */
  protected def prepareBuild(stage: File, version: String, libs: Seq[(String, String)]): Unit = {
    val launcher =
      s"""|#!/usr/bin/env bash
         |
         |if [ -d "/usr/share/reactive-cli/lib" ]; then
         |  if [ "$$LD_LIBRARY_PATH" = "" ]; then
         |    export LD_LIBRARY_PATH="/usr/share/reactive-cli/lib"
         |  else
         |    export LD_LIBRARY_PATH="/usr/share/reactive-cli/lib:$$LD_LIBRARY_PATH"
         |  fi
         |fi
         |
         |$launcherHook
         |
         |exec /usr/share/reactive-cli/bin/rp "$$@"
         |""".stripMargin

    IO.createDirectory(stage / "package" / "usr" / "bin")
    IO.createDirectory(stage / "package" / "usr" / "share" / "reactive-cli" / "bin")
    IO.write(stage / "package" / "usr" / "share" / "reactive-cli" / "bin" / "rp-launcher", launcher)
    AdditionalIO.setExecutable(stage / "package" / "usr" / "share" / "reactive-cli" / "bin" / "rp-launcher")

    IO.write(
      stage / "build",
      s"""|#!/usr/bin/env bash
          |
          |# This is executed within the container. It runs the SBT build and creates a local package directory.
          |
          |set -e
          |
          |export STAGE="$$(pwd)"
          |
          |pushd argonaut
          |sbt argonautNative/publishLocal
          |popd
          |
          |pushd reactive-cli
          |
          |${libs.map { case (p, n) => s"cp -p '$p' '../package/usr/share/reactive-cli/lib/$n'" }.mkString("\n")}
          |
          |if [ "$$LD_LIBRARY_PATH" = "" ]; then
          |  export LD_LIBRARY_PATH="$$STAGE/package/usr/share/reactive-cli/lib"
          |else
          |  export LD_LIBRARY_PATH="$$STAGE/package/usr/share/reactive-cli/lib:$$LD_LIBRARY_PATH"
          |fi
          |
          |$preBuildHook
          |
          |sbt -Dbuild.nativeMode=debug $sbtBuildArgumentsHook clean test package
          |
          |$postBuildHook
          |
          |mv target/output/bin/* ../package/usr/share/reactive-cli/bin/
          |chmod 755 -R ../package/usr/share/reactive-cli/bin
          |ln -s /usr/share/reactive-cli/bin/rp-launcher ../package/usr/bin/rp
          |popd
          |""".stripMargin)

    AdditionalIO.setExecutable(stage / "build")
  }

  protected def parseLibs(libs: Seq[String]): Seq[(String, String)] =
    libs.map(l => l -> l.reverse.takeWhile(_ != '/').reverse)
}

object RpmBuildTarget {
  def normalizeVersion(version: String): String =
    version.replaceAll("-", ".")
}

case class RpmBuildTarget(release: String, requires: String, libs: Seq[String]) extends BuildTarget {
  def prepare(stage: File, info: BuildInfo, version: String): Unit = {
    val libPathToName = parseLibs(libs)

    prepareBuild(stage, version, libPathToName)

    IO.createDirectory(stage / "package" / "BUILD")

    IO.createDirectory(stage / "package" / "RPMS")

    val spec =
      s"""|Summary:        $buildRpmSummary
          |Name:           $buildPackage
          |Version:        ${RpmBuildTarget.normalizeVersion(version)}
          |Release:        $release
          |License:        $buildLicense
          |Source:         %{expand:%%(pwd)}
          |BuildArch:      $buildRpmArch
          |BuildRoot:      %{_tmppath}/%{name}-build
          |Group:          System/Base
          |Vendor:         $buildRpmVendor
          |AutoReqProv:    no
          |Requires:       $requires
          |
          |%define _rpmdir package/RPMS/
          |
          |%description
          |$buildDescription
          |
          |%prep
          |rm -rf "$$RPM_BUILD_ROOT"
          |mkdir -p "$$RPM_BUILD_ROOT"
          |cp -rp "$$STAGE/package/usr" "$$RPM_BUILD_ROOT/usr"
          |
          |%clean
          |rm -rf "$$RPM_BUILD_ROOT"
          |
          |%files
          |%defattr(-,root,root)
          |/usr/bin/rp
          |/usr/share/reactive-cli/bin/rp
          |/usr/share/reactive-cli/bin/rp-launcher
          |${libPathToName.map(e => s"/usr/share/reactive-cli/lib/${e._2}").mkString("\n")}
          |""".stripMargin

    IO.write(stage / "package" / "reactive-cli.spec", spec)

    IO.write(
      stage / "command",
      s"""|#!/usr/bin/env bash
          |
          |# This is executed within the container. It runs the SBT build (via ./build) and creates the .rpm package
          |
          |set -e
          |
          |bash ./build
          |
          |export STAGE="$$(pwd)"
          |
          |rpmbuild -bb package/reactive-cli.spec
          |
          |mv package/RPMS/*/* output/
          |""".stripMargin)

    AdditionalIO.setExecutable(stage / "command")
  }
}

case class DebBuildTarget(distributions: Seq[String], components: String, dependencies: String, libs: Seq[String]) extends BuildTarget {
  val architecture = "amd64"

  def prepare(stage: File, info: BuildInfo, version: String): Unit = {
    val libPathToName = parseLibs(libs)

    prepareBuild(stage, version, libPathToName)

    val control =
      s"""|Package: $buildPackage
          |Version: $version
          |Maintainer: $buildDebMaintainer
          |License: $buildLicense
          |Architecture: $architecture
          |Description: $buildDescription
          |Depends: $dependencies
          |""".stripMargin

    IO.createDirectory(stage / "package" / "DEBIAN")

    IO.write(stage / "package" / "DEBIAN" / "control", control)

    IO.write(
      stage / "command",
      s"""|#!/usr/bin/env bash
          |
          |# This is executed within the container. It runs the SBT build (via .build) and creates the .deb package
          |
          |set -e
          |
          |bash ./build
          |
          |dpkg --build package
          |
          |mv package.deb output/reactive-cli_$version-${distributions.mkString("-")}_$architecture.deb
          |""".stripMargin)

    AdditionalIO.setExecutable(stage / "command")
  }
}