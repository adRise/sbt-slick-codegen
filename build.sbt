import java.lang.management.ManagementFactory

import scala.collection.JavaConverters.*
//import scalariform.formatter.preferences.*

enablePlugins(SbtPlugin)

sbtPlugin := true
name := """sbt-slick-codegen"""
organization := "com.tubitv"

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % Versions.slick,
  "com.typesafe.slick" %% "slick-codegen" % Versions.slick,
  "org.postgresql" % "postgresql" % Versions.postgresql,
  "com.github.docker-java" % "docker-java" % Versions.dockerJava,
)
addSbtPlugin("io.github.davidmweber" % "flyway-sbt" % Versions.flywaySbt)

publishTo := Some(if (isSnapshot.value) Repo.Jfrog.Tubins.sbtDev else Repo.Jfrog.Tubins.sbtRelease)
ThisBuild / versionScheme := Some("early-semver")
Test / publishArtifact := false

scriptedBufferLog := false
scriptedLaunchOpts ++= ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList.filter(
  a => Seq("-Xmx", "-Xms", "-XX", "-Dsbt.log.noformat").exists(a.startsWith)
)
scriptedLaunchOpts ++= Seq("-Dplugin.version=" + version.value, "-Dslick.version=" + Versions.slick)
