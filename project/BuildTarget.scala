import java.util.UUID
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

  protected def launcherExecHook: String = "exec"

  protected def launcherHook: String = ""

  /**
   * Creates a file at stage/build that will build the project but not package it. The launcher should be agnostic
   * about the location it's installed to as long as the relative paths remain the same. This allows makeself
   * to work, which is used for the .tar.gz build.
   */
  protected def prepareBuild(stage: File, version: String, libs: Seq[(String, String)]): Unit = {
    val launcher =
      s"""|#!/usr/bin/env bash
          |
          |set -e
          |
          |SCRIPT_NAME="$${BASH_SOURCE[0]}"
          |
          |if [ -h "$$SCRIPT_NAME" ]; then
          |  SCRIPT_NAME="$$(readlink "$$SCRIPT_NAME")"
          |fi
          |
          |DIR="$$(cd "$$(dirname "$$SCRIPT_NAME")" && cd .. && pwd)"
          |
          |if [ -d "$$DIR/lib" ]; then
          |  if [ "$$LD_LIBRARY_PATH" = "" ]; then
          |    export LD_LIBRARY_PATH="$$DIR/lib"
          |  else
          |    export LD_LIBRARY_PATH="$$DIR/lib:$$LD_LIBRARY_PATH"
          |  fi
          |fi
          |
          |$launcherHook
          |
          |$launcherExecHook "$$DIR/bin/rp" "$$@"
          |""".stripMargin

    val build =
      s"""|#!/usr/bin/env bash
          |
          |# This is executed within the container. It runs the sbt build and creates a local package directory.
          |
          |set -e
          |
          |export STAGE="$$(pwd)"
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
          |sbt -Xmx${Properties.memory}m -Dbuild.nativeMode=${Properties.nativeMode} $sbtBuildArgumentsHook clean cliNative/test cliNative/package
          |
          |$postBuildHook
          |
          |mv cli/native/target/output/bin/* ../package/usr/share/reactive-cli/bin/
          |chmod 755 -R ../package/usr/share/reactive-cli/bin
          |ln -s /usr/share/reactive-cli/bin/rp-launcher ../package/usr/bin/rp
          |popd
          |""".stripMargin

    IO.createDirectory(stage / "package" / "usr" / "bin")
    IO.createDirectory(stage / "package" / "usr" / "share" / "reactive-cli" / "bin")
    IO.createDirectory(stage / "package" / "usr" / "share" / "reactive-cli" / "lib")
    IO.write(stage / "package" / "usr" / "share" / "reactive-cli" / "bin" / "rp-launcher", launcher)
    AdditionalIO.setExecutable(stage / "package" / "usr" / "share" / "reactive-cli" / "bin" / "rp-launcher")
    IO.write(stage / "build", build)
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

    val command =
      s"""|#!/usr/bin/env bash
          |
          |# This is executed within the container. It runs the sbt build (via ./build) and creates the .rpm package
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
          |""".stripMargin

    IO.createDirectory(stage / "package" / "BUILD")
    IO.createDirectory(stage / "package" / "RPMS")
    IO.write(stage / "package" / "reactive-cli.spec", spec)
    IO.write(stage / "command", command)
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

    val command =
      s"""|#!/usr/bin/env bash
          |
          |# This is executed within the container. It runs the sbt build (via .build) and creates the .deb package
          |
          |set -e
          |
          |bash ./build
          |
          |DPKGVER="$$(dpkg --version | sed -e 's/^.*version \\([^ ]*\\) .*$$/\\1/;q')"
          |RECENTDPKG=0
          |dpkg --compare-versions "$$DPKGVER" "lt" "1.19" || RECENTDPKG=1
          |if [[ "$$RECENTDPKG" == "0" ]]
          |then
          |  dpkg-deb -b package
          |else
          |  dpkg-deb -b --no-uniform-compression package
          |fi
          |
          |mv package.deb output/reactive-cli_$version-${distributions.mkString("-")}_$architecture.deb
          |""".stripMargin

    IO.createDirectory(stage / "package" / "DEBIAN")
    IO.write(stage / "package" / "DEBIAN" / "control", control)
    IO.write(stage / "command", command)
    AdditionalIO.setExecutable(stage / "command")
  }
}

case class TarGzSelfContainedExecutableBuildTarget(libs: Seq[String]) extends BuildTarget {
  val architecture = "amd64"

  override def launcherExecHook: String = "exec $DIR/lib/ld-musl-x86_64.so.1"

  def prepare(stage: File, info: BuildInfo, version: String): Unit = {
    val libPathToName = parseLibs(libs)

    prepareBuild(stage, version, libPathToName)

    val marker = s"#__${UUID.randomUUID().toString}__"

    val rp =
      s"""|#!/usr/bin/env bash
          |
          |# This script is generated by the reactive-cli release process.
          |
          |# It generates a random marker and extracts data (tar format)
          |# after that marker into a temporary directory. It then executes
          |# the contents of bin/rp-launcher in that temporary directory.
          |
          |# Finally, when the script exits (via trapping EXIT), it removes
          |# that temporary directory.
          |
          |set -e
          |
          |SCRIPT_NAME="$${BASH_SOURCE[0]}"
          |
          |if [ -h "$$SCRIPT_NAME" ]; then
          |  SCRIPT_NAME="$$(readlink "$$SCRIPT_NAME")"
          |fi
          |
          |DIR="$$(mktemp -d)"
          |
          |trap 'rm -r $$DIR' EXIT
          |
          |LINE="$$(awk '/^$marker$$/{print NR + 1; exit 0; }' $$SCRIPT_NAME)"
          |
          |tail "-n+$$LINE" "$$SCRIPT_NAME" | $$(cd "$$DIR" && tar -x)
          |
          |# We can't exec here as then our trap will not execute
          |"$$DIR/bin/rp-launcher" "$$@"
          |exit "$$?"
          |$marker
          |""".stripMargin

    val command =
      s"""|#!/usr/bin/env bash
          |
          |# This is executed within the container. It runs the sbt build (via .build) and creates the .tar.gz package
          |
          |set -e
          |
          |# Build the package
          |bash ./build
          |
          |# Append it to our self-extracting launcher (rp)
          |tar cf - -C package/usr/share/reactive-cli . >> ./rp
          |
          |# tar it up and move it into the output directory
          |tar czf "output/reactive-cli-$version-Linux-$architecture.tar.gz" -C . ./rp
          |""".stripMargin

    IO.write(stage / "rp", rp)
    AdditionalIO.setExecutable(stage / "rp")
    IO.write(stage / "command", command)
    AdditionalIO.setExecutable(stage / "command")
  }
}
