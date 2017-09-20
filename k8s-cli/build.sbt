import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.HeaderPattern
import scalariform.formatter.preferences.AlignSingleLineCaseStatements


// Disable GC since the CLI is a short-lived process.
nativeGC := "none"

nativeLinkingOptions := Seq("-L", "/Users/felixsatyaputra/workspace/typesafe-fsat/reactive-cli/libhttpsimple/target")

libraryDependencies ++= List(
  "com.github.scopt"  %%% "scopt"    % "3.7.0",
  "io.argonaut"       %%% "argonaut" % "6.3-SNAPSHOT"
)
