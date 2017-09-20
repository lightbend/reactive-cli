import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.HeaderPattern
import scalariform.formatter.preferences.AlignSingleLineCaseStatements

lazy val root = project
  .in(file("."))
  .aggregate(
    `k8s-cli`
  )

lazy val `k8s-cli` = project
  .in(file("k8s-cli"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
