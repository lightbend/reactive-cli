val Versions = new {
  val sbtHeader         = "3.0.1"
  val sbtNativePackager = "1.2.0"
  val sbtScalaNative     = "0.3.3"
  val sbtScalariform     = "1.8.0"
}

addSbtPlugin("com.typesafe.sbt"   % "sbt-native-packager" % Versions.sbtNativePackager)
addSbtPlugin("de.heikoseeberger"  % "sbt-header"          % Versions.sbtHeader)
addSbtPlugin("org.scala-native"   % "sbt-scala-native"    % Versions.sbtScalaNative)
addSbtPlugin("org.scalariform"    % "sbt-scalariform"     % Versions.sbtScalariform)
