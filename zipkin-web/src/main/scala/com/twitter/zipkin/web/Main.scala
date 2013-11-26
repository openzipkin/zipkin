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

import com.twitter.conversions.time._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Filter, Http, Service, Thrift}
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.finatra._
import com.twitter.io.{Files, TempFile}
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import com.twitter.zipkin.gen.ZipkinQuery
import com.twitter.zipkin.builder.{ZooKeeperClientBuilder, QueryClient}
import com.twitter.zipkin.config.{CssConfig, JsConfig}
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}

object Main extends TwitterServer {

  val serverPort = flag("zipkin.web.port", new InetSocketAddress(8080), "Listening port for the zipkin web frontend")

  val rootUrl = flag("zipkin.web.rootUrl", "http://localhost:8080/", "Url where the service is located")
  val pinTtl = flag("zipkin.web.pinTtl", 30.days, "Length of time pinned traces should exist")
  val resourcePathPrefix = flag("zipkin.web.resourcePathPrefix", "/public", "Path used for static resources")

  // TODO: make this idomatic
  val queryClientLocation = flag("zipkin.queryClient.location", "127.0.0.1:9411", "Location of the query server")

  /* Map dirname to content type */
  private val resourceDirs: Map[String, String] = Map[String, String](
    "css"       -> "text/css",
    "img"       -> "image/png",
    "js"        -> "application/javascript",
    "templates" -> "text/plain"
  )

  private[this] val nettyToFinagle =
    Filter.mk[HttpRequest, HttpResponse, Request, Response] { (req, service) =>
      service(Request(req)) map { _.httpResponse }
    }

  def main() {
    val jsConfig = new JsConfig {
      override val pathPrefix = resourcePathPrefix()
    }
    val cssConfig = new CssConfig {
      override val pathPrefix = resourcePathPrefix()
    }

    // TODO: ThriftMux
    val finagledClient = Thrift.newIface[ZipkinQuery.FutureIface]("ZipkinQuery=" + queryClientLocation())

    val app = new App(rootUrl(), pinTtl(), jsConfig, cssConfig, finagledClient, statsReceiver)

    val resource = new Resource(resourceDirs)

    val controllers = new ControllerCollection
    controllers.add(app)

    val appService = new AppService(controllers)
    val fileService = new FileService

    val service = nettyToFinagle andThen fileService andThen appService

    val server = Http.serve(serverPort(), service)
    onExit { server.close() }
    Await.ready(server)
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
