import sbt._
import scala.collection.immutable.Seq

// Disable GC since the CLI is a short-lived process.
nativeGC := "none"

nativeLinkingOptions := {
  val dynamicLinkerOptions =
    Properties
      .dynamicLinker
      .toVector
      .map(dl => s"-Wl,--dynamic-linker=$dl")

  dynamicLinkerOptions ++ Seq(
    "-lcurl",
    "-L", (baseDirectory.value / ".." / "libhttpsimple" / "target").toPath.toAbsolutePath.toString
  ) ++ sys.props.get("nativeLinkingOptions").fold(Seq.empty[String])(_.split(" ").toVector)
}

sourceGenerators in Compile += Def.task {
  val versionFile = (sourceManaged in Compile).value / "ProgramVersion.scala"

  val versionSource =
    """|package com.lightbend.rp.reactivecli
       |
       |object ProgramVersion {
       |  val current = "%s"
       |}
    """.stripMargin.format(version.value)

  IO.write(versionFile, versionSource)

  Seq(versionFile)
}