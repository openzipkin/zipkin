package com.twitter.zipkin.web

import com.twitter.zipkin.config.ZipkinWebConfig
import com.posterous.finatra.FinatraServer.FinatraService
import com.posterous.finatra.{FileHandler, FinatraServer}
import com.twitter.finagle.builder.{ServerBuilder, Server}
import com.twitter.finagle.http.Http
import java.net.InetSocketAddress
import com.twitter.ostrich.admin.{ServiceTracker, Service}
import com.twitter.logging.Logger

class ZipkinWeb(config: ZipkinWebConfig) extends Service {

  val log = Logger.get()
  var server: Option[Server] = None

  def start() {
    val app = config.app

    FinatraServer.register(app)
    FinatraServer.layoutHelperFactory = new ZipkinLayoutHelperFactory

    val finatraService = new FinatraService
    val fileHandler = new FileHandler
    val service = fileHandler andThen finatraService

    server = Some {
      ServerBuilder()
        .codec(Http())
        .bindTo(new InetSocketAddress(config.serverPort))
        .name("ZipkinWeb")
        .build(service)
    }
    log.info("Finatra service started in port: " + config.serverPort)
    ServiceTracker.register(this)
  }

  def shutdown() {
    server.foreach { _.close() }
  }
}


