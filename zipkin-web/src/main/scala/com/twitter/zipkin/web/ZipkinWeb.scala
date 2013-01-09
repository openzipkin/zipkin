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

import com.twitter.finagle.http.Http
import com.twitter.finagle.builder.{ServerBuilder, Server}
import com.twitter.finatra_core.{AbstractFinatraController, ControllerCollection}
import com.twitter.finatra._
import com.twitter.ostrich.admin
import com.twitter.logging.Logger
import com.twitter.io.{Files, TempFile}
import com.twitter.util.Future
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.finagle.tracing.Tracer

class ZipkinWeb(
  app: App,
  resource: Resource,
  serverPort: Int,
  tracerFactory: Tracer.Factory
) extends admin.Service {

  val log = Logger.get()
  var server: Option[Server] = None

  val controllers = new ControllerCollection[Request, Future[Response], Future[HttpResponse]]

  def start() {

    register(resource)
    register(app)

    val finatraService = new AppService(controllers)
    val service = finatraService

    server = Some {
      ServerBuilder()
        .codec(Http())
        .bindTo(new InetSocketAddress(serverPort))
        .name("ZipkinWeb")
        .tracerFactory(tracerFactory)
        .build(service)
    }
    log.info("Finatra service started in port: " + serverPort)
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


