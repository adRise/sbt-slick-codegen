package com.tubitv

import sbt.Logger

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scala.util.Success

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.{Frame, PortBinding}
import com.github.dockerjava.core.DockerClientBuilder

object PostgresContainer {

  private val isRunning = new AtomicBoolean(false)
  def run(exportPort: Int, password: String, postgresVersion: String, logger: Logger): AutoCloseable = {
    if (isRunning.get()) {
      () => {}
    } else {
      val dockerClient = DockerClientBuilder.getInstance.build
      val container = dockerClient
        .createContainerCmd(s"postgres:$postgresVersion")
        .withEnv(s"POSTGRES_PASSWORD=$password")

      container.getHostConfig
        .withPortBindings(PortBinding.parse(s"$exportPort:5432"))

      val containerId = container.exec().getId
      logger.info(s"Starting docker container:${containerId.substring(0, 12)} [postgres:$postgresVersion]")

      dockerClient.startContainerCmd(containerId).exec()

      val ready = Promise[Unit]()
      dockerClient
        .logContainerCmd(containerId)
        .withFollowStream(true)
        .withSince(0)
        .withStdOut(true)
        .withStdErr(true)
        .exec(new ResultCallback.Adapter[Frame] {
          override def onNext(`object`: Frame): Unit = {
            val lines = new String(`object`.getPayload).split("\n")
            if (lines.exists(_.endsWith("database system is ready to accept connections"))) {
              ready.complete(Success(()))
              this.close()
            }
          }
        })

      Await.result(ready.future, Duration.Inf)
      isRunning.set(true)
      logger.info("Postgres start success")

      () => {
        try {
          dockerClient.stopContainerCmd(containerId).exec()
        } catch {
          case _: Throwable =>
        }
        dockerClient.removeContainerCmd(containerId).exec()
        isRunning.set(false)
        logger.info(s"Docker container [postgres:$postgresVersion] is stopped")
      }
    }
  }
}
