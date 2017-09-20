import sbt._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.HeaderPattern
import scalariform.formatter.preferences.AlignSingleLineCaseStatements

lazy val commonSettings = Seq(
  scalaVersion := "2.11.11",

  libraryDependencies ++= List(
    "com.lihaoyi"       %%% "utest"    % "0.4.8" % "test"
  ),

  ScalariformKeys.preferences :=
    ScalariformKeys.preferences.value
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100),

  HeaderPlugin.autoImport.headers := Map(
    "scala" -> (
      HeaderPattern.cStyleBlockComment,
      """|/*
         | * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
         | * No information contained herein may be reproduced or transmitted in any form
         | * or by any means without the express written permission of Lightbend, Inc.
         | */
         |
         |""".stripMargin
      ),
    "conf" -> (
      HeaderPattern.hashLineComment,
      """|# Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
         |# No information contained herein may be reproduced or transmitted in any form
         |# or by any means without the express written permission of Lightbend, Inc.
         |
         |""".stripMargin
      )
  ),

  testFrameworks += new TestFramework("utest.runner.Framework")
)

lazy val root = project
  .in(file("."))
  .aggregate(
    `libhttpsimple-bindings`,
    `k8s-cli`
  )

lazy val `libhttpsimple-bindings` = project
  .in(file("libhttpsimple-bindings"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
  .settings(commonSettings)

lazy val `k8s-cli` = project
  .in(file("k8s-cli"))
  .enablePlugins(ScalaNativePlugin, AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(Seq(
    // Disable GC since the CLI is a short-lived process.
    nativeGC := "none",

    nativeLinkingOptions := Seq("-L", (file("libhttpsimple") / "target").toString),

    libraryDependencies ++= List(
      "com.github.scopt"  %%% "scopt"    % "3.7.0",
      "io.argonaut"       %%% "argonaut" % "6.3-SNAPSHOT"
    )
  ))
  .dependsOn(`libhttpsimple-bindings`)
