package com.github.tototoshi.sbt.slick

import sbt.*

import _root_.io.github.davidmweber.FlywayPlugin
import com.tubitv.PostgresContainer

trait PluginDBSupport {

  protected final val dbUser = "postgres"
  protected final val dbPass = "password"

  protected val postgresDbUrl = settingKey[String]("The database urlt")
  protected val stopDb = taskKey[Unit]("Start the postgres docker container and run flyway migrate")
  protected val startDb = taskKey[Unit]("Stop and remove the postgres container")

  lazy val postgresContainerPort = settingKey[Int]("The port of postgres port")
  lazy val postgresImage = settingKey[String]("The postgres image name")
  lazy val postgresVersion = settingKey[String]("The postgres version")

  protected def dbSettings: Seq[sbt.Setting[_]] = {
    import FlywayPlugin.autoImport._
    FlywayPlugin.projectSettings ++
      Seq(
        flywayDefaults / Keys.logLevel := Level.Warn,
        postgresDbUrl := s"jdbc:postgresql://127.0.0.1:${postgresContainerPort.value}/postgres",
        postgresContainerPort := 15432,
        postgresImage := "postgres",
        postgresVersion := "13.7",
        flywayUrl := postgresDbUrl.value,
        flywayUser := dbUser,
        flywayPassword := dbPass,
        flywayLocations := Seq(s"filesystem:${(Compile / Keys.resourceDirectory).value.getAbsoluteFile}/db/migration"),
        startDb := Def
          .sequential(
            Def.task {
              PostgresContainer.start(
                exportPort = postgresContainerPort.value,
                password = dbPass,
                postgresImage = postgresImage.value,
                postgresVersion = postgresVersion.value,
                logger = Keys.streams.value.log
              )
            },
            FlywayPlugin.autoImport.flywayMigrate
          )
          .value,
        stopDb := {
          PostgresContainer.stop(Keys.streams.value.log)
        }
      )
  }

  /** Run the task with db ready , will start the postgres docker, and run flyway migrate before the task
    * and also, it will make sure stop and remove the container after the task
    * @param task the task to be executed
    * @tparam A
    * @return
    */
  protected def withDb[A](task: Def.Initialize[Task[A]]): Def.Initialize[Task[A]] = Def.taskDyn {
    task
      .dependsOn(startDb)
      .doFinally(stopDb.taskValue)
  }

}
