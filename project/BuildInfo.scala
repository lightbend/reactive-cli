import sbt._
import scala.collection.immutable.Seq

import AdditionalIO._

object BuildInfo {
  val Builds =
    Seq(
      MuslBuild.tgz("tgz"),

      MuslBuild.rpm("centos-6", "el6", "bash"),

      BuildInfo(
        name = "centos-7",
        baseImage = "centos:7",
        install = s"""|RUN \\
                      |  curl -s https://releases.llvm.org/3.8.0/clang+llvm-3.8.0-linux-x86_64-centos6.tar.xz | tar xf - --strip-components=1 -J -C /usr/local/ && \\
                      |  curl -s https://bintray.com/sbt/rpm/rpm > /etc/yum.repos.d/bintray-sbt-rpm.repo && \\
                      |  yum install -y bc gcc gcc-c++ epel-release git java-1.8.0-openjdk-headless libcurl-devel libunwind-devel make openssl-devel rpm-build sbt which && \\
                      |  yum install -y jq && \\
                      |  git clone https://code.googlesource.com/re2 /opt/re2 && \\
                      |  pushd /opt/re2 && \\
                      |  git checkout 2017-11-01 && \\
                      |  make && \\
                      |  make install && \\
                      |  popd
                      |RUN yum install -y libstdc++-devel libstdc++-static
                      |""".stripMargin,
        target = new RpmBuildTarget("el7", "bash,libunwind,libcurl", Seq("/opt/re2/obj/so/libre2.so.0"))),

      BuildInfo(
        name = "debian-8",
        baseImage = "debian:8",
        install = s"""|RUN \\
                      |  apt-get -y update && \\
                      |  apt-get -y install apt-transport-https && \\
                      |  echo "deb http://ftp.de.debian.org/debian jessie-backports main" > /etc/apt/sources.list.d/jessie-backports.list && \\
                      |  echo "deb https://dl.bintray.com/sbt/debian /" > /etc/apt/sources.list.d/sbt.list && \\
                      |  apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \\
                      |  apt-get -y update && \\
                      |  apt-get -y install -t jessie-backports jq openjdk-8-jre-headless ca-certificates-java && \\
                      |  apt-get -y install bc build-essential clang++-3.8 libcurl4-openssl-dev libgc-dev libre2-dev libunwind8-dev sbt
                      |""".stripMargin,
        target = DebBuildTarget(Seq("jessie"), "main", "bash,libcurl3,libre2-1,libunwind8", Seq.empty)),

      BuildInfo(
        name = "debian-9",
        baseImage = "debian:9",
        install = s"""|RUN \\
                      |  apt-get -y update && \\
                      |  apt-get -y install apt-transport-https gnupg && \\
                      |  echo "deb https://dl.bintray.com/sbt/debian /" > /etc/apt/sources.list.d/sbt.list && \\
                      |  apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \\
                      |  apt-get -y update && \\
                      |  apt-get -y install bc build-essential clang++-3.9 jq libcurl4-openssl-dev libgc-dev libre2-dev libunwind8-dev openjdk-8-jdk-headless sbt
                      |""".stripMargin,
        target = DebBuildTarget(Seq("stretch"), "main", "bash,libcurl3,libre2-3,libunwind8", Seq.empty)),

      MuslBuild.deb("ubuntu-older", Seq("trusty", "utopic", "vivid", "wily"), "main", "bash"),

      BuildInfo(
        name = "ubuntu-16-04",
        baseImage = "ubuntu:16.04",
        install = s"""|RUN \\
                      |  apt-get -y update && \\
                      |  apt-get -y install apt-transport-https && \\
                      |  echo "deb https://dl.bintray.com/sbt/debian /" > /etc/apt/sources.list.d/sbt.list && \\
                      |  apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \\
                      |  apt-get -y update && \\
                      |  apt-get -y install bc build-essential jq openjdk-8-jre-headless ca-certificates-java clang++-3.8 libcurl4-openssl-dev libgc-dev libre2-dev libunwind8-dev sbt
                      |""".stripMargin,
        target = DebBuildTarget(Seq("xenial"), "main", "bash,libre2-1v5,libunwind8,libcurl3", Seq.empty)),

      BuildInfo(
        name = "ubuntu-16-10",
        baseImage = "ubuntu:16.10",
        install = s"""|RUN \\
                      |  apt-get -y update && \\
                      |  apt-get -y install apt-transport-https dirmngr && \\
                      |  echo "deb https://dl.bintray.com/sbt/debian /" > /etc/apt/sources.list.d/sbt.list && \\
                      |  apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \\
                      |  apt-get -y update && \\
                      |  apt-get -y install bc build-essential jq openjdk-8-jre-headless ca-certificates-java clang-3.8 libcurl4-openssl-dev libgc-dev libre2-dev libunwind8-dev sbt
                      |""".stripMargin,
        target = DebBuildTarget(Seq("yakkety"), "main", "bash,libre2-2,libunwind8,libcurl3", Seq.empty)),

      BuildInfo(
        name = "ubuntu-17-04_17-10",
        baseImage = "ubuntu:17.04",
        install = s"""|RUN \\
                      |  apt-get -y update && \\
                      |  apt-get -y install apt-transport-https dirmngr && \\
                      |  echo "deb https://dl.bintray.com/sbt/debian /" > /etc/apt/sources.list.d/sbt.list && \\
                      |  apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \\
                      |  apt-get -y update && \\
                      |  apt-get -y install bc build-essential jq openjdk-8-jre-headless ca-certificates-java clang-3.8 libcurl4-openssl-dev libgc-dev libre2-dev libunwind8-dev sbt
                      |""".stripMargin,
        target = DebBuildTarget(Seq("zesty", "artful"), "main", "bash,libre2-3,libunwind8,libcurl3", Seq.empty)),

      BuildInfo(
        name = "ubuntu-18-04",
        baseImage = "ubuntu:18.04",
        install = s"""|RUN \\
                      |  apt-get -y update && \\
                      |  apt-get -y install apt-transport-https dirmngr ca-certificates && \\
                      |  echo "deb https://dl.bintray.com/sbt/debian /" > /etc/apt/sources.list.d/sbt.list && \\
                      |  apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \\
                      |  apt-get -y update && \\
                      |  apt-get -y install bc build-essential jq openjdk-8-jre-headless ca-certificates-java clang-3.9 libcurl4-openssl-dev libgc-dev libre2-dev libunwind8-dev sbt
                      |""".stripMargin,
        target = DebBuildTarget(Seq("bionic"), "main", "bash,libre2-4,libunwind8,libcurl4", Seq.empty)))

  def initialize(root: File): Unit = {
    val ivyDir = root / "target" / ".ivy2" / "cache"
    val sbtDir = root / "target" / ".sbt" / "launchers"

    if (!ivyDir.isDirectory && (Path.userHome / ".ivy2" / "cache").isDirectory) {
      IO.createDirectory(root / "target" / ".ivy2")

      val ivyTempDir = root / "target" / ".ivy2" / "temporary"

      IO.copyDirectory(Path.userHome / ".ivy2" / "cache", ivyTempDir)

      IO.move(ivyTempDir, ivyDir)
    }

    if (!sbtDir.isDirectory && (Path.userHome / ".sbt" / "launchers").isDirectory) {
      IO.createDirectory(root / "target" / ".sbt")

      val sbtTempDir = root / "target" / ".sbt" / "temporary"

      IO.copyDirectory(Path.userHome / ".sbt" / "launchers", sbtTempDir)

      IO.move(sbtTempDir, sbtDir)
    }
  }
}

object MuslBuild {
  private val image = "openjdk:8u121-jre-alpine"

  private val install =
    s"""|RUN \\
        |  apk --update add bash build-base clang curl-dev dpkg gc-dev git jq libc-dev musl-dev rpm tar wget && \\
        |  apk add libunwind-dev --update-cache --repository http://nl.alpinelinux.org/alpine/edge/main && \\
        |  git clone https://code.googlesource.com/re2 /opt/re2 && \\
        |  cd /opt/re2 && \\
        |  git checkout 2017-11-01 && \\
        |  CXX=clang++ make && \\
        |  make install && \\
        |  cd - && \\
        |  mkdir /opt/sbt && \\
        |  wget -qO - --no-check-certificate "https://dl.bintray.com/sbt/native-packages/sbt/0.13.15/sbt-0.13.15.tgz" | tar xz -C /opt/sbt --strip-components=1
        |
        |ENV PATH /opt/sbt/bin:$${PATH}
        |
        |# Have to hack around a few things -- make sure our dynamic linker is in place,
        |# initialize the RPM database, and make /var/tmp writable for reactive-cli user to use
        |
        |RUN \\
        |  mkdir -p /usr/share/reactive-cli/lib/ && chmod 777 /usr/share/reactive-cli/lib/ && \\
        |  rpm --quiet -qa && \\
        |  chmod -R 777 /var/tmp
        |""".stripMargin

  private val libs =
    Seq(
      "/opt/re2/obj/so/libre2.so.0",
      "/usr/lib/libunwind.so.8",
      "/usr/lib/libunwind-x86_64.so.8",
      "/usr/lib/libgc.so.1",
      "/usr/lib/libstdc++.so.6",
      "/usr/lib/libgcc_s.so.1",
      "/lib/ld-musl-x86_64.so.1",
      "/lib/libc.musl-x86_64.so.1",
      "/usr/lib/libcrypto.so.38",
      "/usr/lib/libcurl.so.4",
      "/usr/lib/libssh2.so.1",
      "/usr/lib/libssl.so.39",
      "/lib/libz.so.1")

  private val preBuild =
    """|cp -p /lib/ld-musl-x86_64.so.1 /usr/share/reactive-cli/lib/
       |""".stripMargin

  private val sbtBuildArguments =
    """-Dbuild.dynamicLinker=/usr/share/reactive-cli/lib/ld-musl-x86_64.so.1"""

  def deb(name: String, distributions: Seq[String], components: String, dependencies: String): BuildInfo = new BuildInfo(
    name = name,
    baseImage = image,
    install = install,
    target = new DebBuildTarget(distributions, components, dependencies, libs) {
      override protected def preBuildHook: String = preBuild

      override protected def sbtBuildArgumentsHook: String = sbtBuildArguments
    })

  def rpm(name: String, release: String, requires: String): BuildInfo = new BuildInfo(
    name = name,
    baseImage = image,
    install = install,
    target = new RpmBuildTarget(release, requires, libs) {
      override protected def preBuildHook: String = preBuild

      override protected def sbtBuildArgumentsHook: String = sbtBuildArguments
    })

  def tgz(name: String): BuildInfo = new BuildInfo(
    name = name,
    baseImage = image,
    install = install,
    target = new TarGzSelfContainedExecutableBuildTarget(libs) {
      override protected def preBuildHook: String = preBuild

      override protected def sbtBuildArgumentsHook: String = sbtBuildArguments
    })
}

case class BuildInfo(name: String, baseImage: String, install: String, target: BuildTarget) {
  private val dockerVersion = "latest"

  val dockerBuildImage = s"reactive-cli-build-$name:$dockerVersion"
  val dockerTaggedBuildImage = s"lightbend-docker-registry.bintray.io/rp/$dockerBuildImage"

  /**
   * Builds and tags the Docker image. This needs to then be manually pushed up to Bintray.
   */
  def build(stage: File): String = {
    val dockerFile =
      s"""|FROM $baseImage
          |LABEL REBUILD=20171108-01
          |MAINTAINER info@lightbend.com
          |
          |$install
          |RUN mkdir -p /root
          |WORKDIR /root/stage
          |CMD ["./command"]
          |""".stripMargin

    IO.createDirectory(stage / ".context")

    IO.write(stage / ".context" / "Dockerfile", dockerFile)

    runProcess("docker", "pull", baseImage)

    runProcessCwd(stage / ".context", "docker", "build", "-t", dockerBuildImage, (stage / ".context").getPath)

    runProcess("docker", "tag", dockerBuildImage, dockerTaggedBuildImage)

    dockerTaggedBuildImage
  }

  /**
   * Runs the build with the latest published Docker image.
   */
  def run(root: File, stage: File, version: String, log: Logger): Seq[File] = {
    def clearStage(): Unit = {
      IO.delete(stage)
      IO.createDirectory(stage)
    }

    def copyProject(): Unit = {
      log.info(s"[$name] copying project")

      val filter =
        new SimpleFileFilter(f => f.getPath.contains(s"${Path.sep}target") || f.getPath.contains(s"${Path.sep}.git"))

      for {
        source <- (root ** -(DirectoryFilter || filter)).get
        destination <- Path.rebase(root, stage / "reactive-cli")(source)
      } IO.copyFile(source, destination)
    }

    def pullImage(): Unit = {
      runProcess("docker", "pull", dockerTaggedBuildImage)
    }

    def run(): Vector[File] = {
      IO.createDirectory(stage / "output")

      target.prepare(stage, this, version)

      runProcess(
        "docker",
        "run",
        "--rm=true",

        "--env", "BINTRAY_USER=none", // Quiet the Bintray warnings from sbt-bintray
        "--env", "BINTRAY_PASS=none",

        "-v", s"$stage:/root/stage",
        "-v", s"${root / "target" / ".ivy2" / "cache"}:/root/.ivy2/cache",
        "-v", s"${root / "target" / ".sbt" / "launchers"}:/root/.sbt/launchers",
        dockerTaggedBuildImage)

      IO.listFiles(stage / "output").toVector
    }

    log.info(s"[$name] building")

    clearStage()

    copyProject()

    pullImage()

    run()
  }
}