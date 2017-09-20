import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.HeaderPattern
import scalariform.formatter.preferences.AlignSingleLineCaseStatements

lazy val root = project
  .in(file("."))
  .aggregate(
    `libhttpsimple-bindings`,
    `k8s-cli`
  )

lazy val `libhttpsimple-bindings` = project
  .in(file("libhttpsimple-bindings"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)

lazy val `k8s-cli` = project
  .in(file("k8s-cli"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
  .dependsOn(`libhttpsimple-bindings`)
