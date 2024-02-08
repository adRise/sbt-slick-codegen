package com.tubitv

import sbt.Logger

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Success, Try}

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.{Frame, PortBinding, PullResponseItem}
import com.github.dockerjava.core.DockerClientBuilder

object PostgresContainer {

  private val runningDb = new AtomicReference[Option[String]](None)

  def start(exportPort: Int, password: String, postgresVersion: String, logger: Logger): Unit = {
    runningDb.get() match {
      case None =>
        val image = s"postgres:$postgresVersion"
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
