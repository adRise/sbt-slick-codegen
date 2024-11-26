package com.tubitv

import sbt.Logger

import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.DurationInt
import scala.util.{Try, Using}

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.{PortBinding, PullResponseItem}
import com.github.dockerjava.core.DockerClientBuilder

object PostgresContainer {

  private val runningDb = new AtomicReference[Option[String]](None)

  def start(exportPort: Int, password: String, postgresImage: String, postgresVersion: String, logger: Logger): Unit = {
    runningDb.get() match {
      case None =>
        val image = s"$postgresImage:$postgresVersion"
        val dockerClient = DockerClientBuilder.getInstance.build
        Try(dockerClient.inspectImageCmd(image).exec()).recover {
          case _: NotFoundException =>
            logger.info(s"Pulling image ${image} ...")
            val done = Promise[Unit]
            dockerClient
              .pullImageCmd(image)
              .exec(new ResultCallback.Adapter[PullResponseItem] {
                override def onComplete(): Unit = done.success(())
              })

            Await.result(done.future, 5.minutes)
            logger.info(s"Image $image pull done")
        }

        val container = dockerClient
          .createContainerCmd(image)
          .withEnv(s"POSTGRES_PASSWORD=$password")

        container.getHostConfig
          .withPortBindings(PortBinding.parse(s"$exportPort:5432"))

        val containerId = container.exec().getId
        if (runningDb.compareAndSet(None, Some(containerId))) {
          logger.info(s"Starting docker container:${containerId.substring(0, 12)} [postgres:$postgresVersion]")

          dockerClient.startContainerCmd(containerId).exec()

          var isReady = false
          val deadLine = 3.minutes.fromNow
          while (!isReady) {
            Class.forName("org.postgresql.Driver")
            val url = s"jdbc:postgresql://127.0.0.1:${exportPort}/postgres"
            Using(DriverManager.getConnection(url, "postgres", password))(_ => ()).toOption match {
              case Some(_) =>
                isReady = true
              case _ =>
                if (deadLine.isOverdue()) {
                  throw new Exception("Postgres container is not ready after 3 minutes")
                }
                Thread.sleep(1000)
            }
          }
          logger.info("Docker container postgres started")
        }
      case _ =>
    }
  }

  def stop(logger: Logger): Unit = {
    runningDb.get() match {
      case Some(id) =>
        val dockerClient = DockerClientBuilder.getInstance.build
        try {
          dockerClient.stopContainerCmd(id).exec()
        } catch {
          case _: Throwable =>
        }
        dockerClient.removeContainerCmd(id).exec()
        runningDb.set(None)
        logger.info(s"Docker container [postgres] is stopped")
      case _ =>
    }
  }
}
