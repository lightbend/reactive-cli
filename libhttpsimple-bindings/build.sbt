import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.HeaderPattern
import scalariform.formatter.preferences.AlignSingleLineCaseStatements


scalaVersion := "2.11.11"

// Disable GC since the CLI is a short-lived process.
nativeGC := "none"

nativeLinkingOptions := Seq("-L", "/Users/felixsatyaputra/workspace/typesafe-fsat/reactive-cli/libhttpsimple/target")

libraryDependencies ++= List(
  "com.lihaoyi"       %%% "utest"    % "0.4.8" % "test"
)

ScalariformKeys.preferences :=
  ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)

testFrameworks += new TestFramework("utest.runner.Framework")
