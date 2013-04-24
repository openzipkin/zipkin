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

import com.twitter.finagle.http.{RichHttp, Http, Request => FinagleRequest}
import com.twitter.finagle.builder.{ServerBuilder, Server}
import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.finagle.tracing.Tracer
import com.twitter.finagle.{Service, exception}
import com.twitter.finatra._
import com.twitter.ostrich.admin
import com.twitter.logging.Logger
import com.twitter.io.{Files, TempFile}
import java.net.InetSocketAddress

class ZipkinWeb(
  app: App,
  resource: Resource,
  serverPort: Int,
  tracer: Tracer,
  exceptionMonitorFactory: exception.MonitorFactory,
  queryClient: Service[_,_]
) extends admin.Service {

  val log = Logger.get()
  var server: Option[Server] = None

  def start() {
    val controllers = new ControllerCollection
    controllers.add(app)

    val appService = new AppService(controllers)
    val fileService = new FileService

    val service = fileService andThen appService

    server = Some {
      ServerBuilder()
        .codec(new RichHttp[FinagleRequest](Http()))
        .bindTo(new InetSocketAddress(serverPort))
        .name("ZipkinWeb")
        .tracer(tracer)
        .monitor(exceptionMonitorFactory)
        .build(service)
    }
    log.info("Finatra service started in port: " + serverPort)
  }

  def shutdown() {
    server.foreach { _.close() }
    queryClient.close()
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


