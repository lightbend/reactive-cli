import sbt._

object Dependencies {
  val akkaVersion = "2.5.20"
  val akkaManagementVersion = "0.20.0"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
  val akkaSlj4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  val akkaManagement = "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion
  val akkaBootstrap = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion
  val akkaDiscoveryDns = "com.lightbend.akka.discovery" %% "akka-discovery-dns" % akkaManagementVersion
  val akkaClusterHttp = "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion

  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % Test
}
