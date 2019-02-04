// https://github.com/akka/akka-management/tree/master/bootstrap-demo/kubernetes-dns

import Dependencies._
import scala.sys.process.Process
import scala.util.control.NonFatal

ThisBuild / version      := "0.1.6"
ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.12.7"

lazy val check = taskKey[Unit]("check")
lazy val generateYaml = taskKey[Unit]("generateYaml")

lazy val root = (project in file("."))
  .enablePlugins(SbtReactiveAppPlugin)
  .settings(
    name := "bootstrap-kubernetes-dns-demo",
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Yno-adapted-args",
    ),
    libraryDependencies ++= Seq(
      akkaManagement,
      akkaClusterHttp,
      akkaCluster,
      akkaClusterSharding,
      akkaClusterTools,
      akkaDiscoveryDns,
      akkaSlj4j,
      logback,
      scalaTest
    ),
    enableAkkaClusterBootstrap := true,

    // run nativeLink in the host build first
    generateYaml := {
      val s = streams.value
      val nm = name.value
      val v = version.value
      val namespace = "reactivelibtest1"
      val rpPath = file(sys.props("reactiveclipath")) / "reactive-cli-out"
      val out = Process(s"$rpPath generate-kubernetes-resources --registry-use-local --generate-all $nm:$v --pod-controller-replicas 3 --stacktrace").!!
      val x =
        if (!Deckhand.isOpenShift)
          out.replaceAllLiterally("imagePullPolicy: IfNotPresent", "imagePullPolicy: Never")
        else out
          .replaceAllLiterally("imagePullPolicy: IfNotPresent", "imagePullPolicy: Always")
          .replaceAllLiterally("image: \"" + s"$nm:$v" + "\"", s"image: docker-registry-default.centralpark.lightbend.com/$namespace/$nm:$v")
      s.log.info("generated YAML: " + x)
      IO.write(target.value / "temp.yaml", x)
    },

    // this logic was taken from test.sh
    check := {
      val s = streams.value
      val nm = name.value
      val v = version.value
      val namespace = "reactivelibtest1"
      val kubectl = Deckhand.kubectl(s.log)
      val docker = Deckhand.docker(s.log)

      try {
        if (!Deckhand.isOpenShift) {
          kubectl.tryCreate(s"namespace $namespace")
          kubectl.setCurrentNamespace(namespace)
        } else {
          // work around: /rp-start: line 60: /opt/docker/bin/bootstrap-kapi-demo: Permission denied
          kubectl.command(s"adm policy add-scc-to-user anyuid system:serviceaccount:$namespace:default")
          kubectl.command(s"policy add-role-to-user system:image-builder system:serviceaccount:$namespace:default")
          docker.tag(s"$nm:$v docker-registry-default.centralpark.lightbend.com/$namespace/$nm:$v")
          docker.push(s"docker-registry-default.centralpark.lightbend.com/$namespace/$nm")
        }
        kubectl.apply(target.value / "temp.yaml")
        kubectl.waitForPods(3)
        kubectl.describe("pods")
        kubectl.checkAkkaCluster(3, _.contains(nm))
      } finally {
        kubectl.delete(s"services,pods,deployment --all --namespace $namespace")
        kubectl.waitForPods(0)
      }
    }
  )
