package com.github.tototoshi.sbt.slick

import sbt.*

import _root_.io.github.davidmweber.FlywayPlugin
import com.tubitv.PostgresContainer

trait PluginDBSupport {

  protected final val dbUser = "postgres"
  protected final val dbPass = "password"

  protected val postgresDbUrl = settingKey[String]("The database urlt")

  lazy val postgresContainerPort = settingKey[Int]("The port of postgres port")
  lazy val postgresVersion = settingKey[String]("The postgres version")

  protected def dbSettings = {
    import FlywayPlugin.autoImport._
    FlywayPlugin.projectSettings ++
      Seq(
        postgresDbUrl := s"jdbc:postgresql://127.0.0.1:${postgresContainerPort.value}/postgres",
        postgresContainerPort := 15432,
        postgresVersion := "13.7",
        flywayUrl := postgresDbUrl.value,
        flywayUser := dbUser,
        flywayPassword := dbPass,
        flywayLocations := Seq(s"filesystem:${(Compile / Keys.resourceDirectory).value.getAbsoluteFile}/db/migration")
      )
  }

  /** Run the task with db ready , will start the postgres docker, and run flyway migrate before the task
    * and also, it will make sure stop and remove the container after the task
    * @param task the task to be executed
    * @tparam A
    * @return
    */
  protected def withDb[A](task: Def.Initialize[Task[A]]): Def.Initialize[Task[A]] = Def.taskDyn {
    val containerClosable = Def.task {
      PostgresContainer.run(
        exportPort = postgresContainerPort.value,
        password = dbPass,
        postgresVersion = postgresVersion.value,
        logger = Keys.streams.value.log
      )
    }.value

    Def
      .sequential(FlywayPlugin.autoImport.flywayMigrate, task)
      .andFinally(containerClosable.close())
  }

}
