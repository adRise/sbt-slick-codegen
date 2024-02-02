import java.lang.management.ManagementFactory

import scala.collection.JavaConverters.*
//import scalariform.formatter.preferences.*

enablePlugins(SbtPlugin)

//scalariformPreferences := scalariformPreferences.value
//  .setPreference(AlignSingleLineCaseStatements, true)
//  .setPreference(DoubleIndentConstructorArguments, true)
//  .setPreference(DanglingCloseParenthesis, Preserve)

sbtPlugin := true
name := """sbt-slick-codegen"""
organization := "com.tubitv"
version := "0.0.1-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % Versions.slick,
  "com.typesafe.slick" %% "slick-codegen" % Versions.slick,
  "org.postgresql" % "postgresql" % Versions.postgresql,
  "com.github.docker-java" % "docker-java" % Versions.dockerJava,
)
addSbtPlugin("io.github.davidmweber" % "flyway-sbt" % Versions.flywaySbt)

publishTo := Some(if (isSnapshot.value) Repo.Jfrog.Tubins.sbtDev else Repo.Jfrog.Tubins.sbtRelease)

Test / publishArtifact := false

scriptedBufferLog := false
scriptedLaunchOpts ++= ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList.filter(
  a => Seq("-Xmx", "-Xms", "-XX", "-Dsbt.log.noformat").exists(a.startsWith)
)
scriptedLaunchOpts ++= Seq("-Dplugin.version=" + version.value, "-Dslick.version=" + Versions.slick)
