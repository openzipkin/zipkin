/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.web

import com.twitter.common.zookeeper.ServerSetImpl
import com.twitter.finagle.http.Http
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder, Server}
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster
import com.twitter.finatra_core.{AbstractFinatraController, ControllerCollection}
import com.twitter.finatra._
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.ostrich.admin
import com.twitter.logging.Logger
import com.twitter.io.{Files, TempFile}
import com.twitter.zipkin.config.ZipkinWebConfig
import com.twitter.zipkin.gen
import com.twitter.util.Future
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.HttpResponse
import scala.Left
import scala.Right
import scala.Some

class ZipkinWeb(config: ZipkinWebConfig) extends admin.Service {

  val log = Logger.get()
  var server: Option[Server] = None

  val controllers = new ControllerCollection[Request, Future[Response], Future[HttpResponse]]

  def start() {
    val clientBuilder = ClientBuilder()
      .codec(ThriftClientFramedCodec())
      .hostConnectionLimit(config.hostConnectionLimit)
      .tracerFactory(config.tracerFactory)

    val clientService = config.queryClient match {
      case Left(address) => {
        clientBuilder.hosts(address)
          .build()
      }
      case Right(zk) => {
        val serverSet = new ServerSetImpl(zk, config.queryServerSetPath)
        val cluster = new ZookeeperServerSetCluster(serverSet) {
          override def ready() = super.ready
        }
        clientBuilder.cluster(cluster)
          .build()
      }
    }

    val client = new gen.ZipkinQuery.FinagledClient(clientService)

    val resource = config.resource
    val app = config.appConfig(client)

    register(resource)
    register(app)

    val finatraService = new AppService(controllers)
    val service = finatraService

    server = Some {
      ServerBuilder()
        .codec(Http())
        .bindTo(new InetSocketAddress(config.serverPort))
        .name("ZipkinWeb")
        .tracerFactory(config.tracerFactory)
        .build(service)
    }
    log.info("Finatra service started in port: " + config.serverPort)
    ServiceTracker.register(this)
  }

  def shutdown() {
    server.foreach { _.close() }
  }

  def register(app: AbstractFinatraController[Request, Future[Response], Future[HttpResponse]]) {
    controllers.add(app)
  }
}

class Resource(resourceDirs: Map[String, String]) extends Controller {
  resourceDirs.foreach { case (dir, contentType) =>
    get("/public/" + dir + "/:id") { request =>
      val file = TempFile.fromResourcePath("/public/" + dir + "/" + request.params("id"))
      if (file.exists()) {
        render.status(200).body(Files.readBytes(file)).header("Content-Type", contentType).toFuture
      } else {
        render.status(404).body("Not Found").toFuture
      }
    }
  }
}


