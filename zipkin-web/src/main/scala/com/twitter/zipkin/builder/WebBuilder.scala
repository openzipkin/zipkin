package com.twitter.zipkin.builder

import com.twitter.conversions.time._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.util.Duration
import com.twitter.zipkin.gen
import com.twitter.zipkin.config.{CssConfig, JsConfig}
import com.twitter.zipkin.web.{Resource, ZipkinWeb, App}

case class WebBuilder(
  rootUrl: String,
  queryClientBuilder: Builder[ClientBuilder.Complete[ThriftClientRequest, Array[Byte]]],
  pinTtl: Duration = 30.days,
  resourcePathPrefix: String = "/public",
  serverBuilder: ZipkinServerBuilder = ZipkinServerBuilder(8080, 9902)
) extends Builder[ZipkinWeb] {

  /* Map dirname to content type */
  private val resourceDirs: Map[String, String] = Map[String, String](
    "css"       -> "text/css",
    "img"       -> "image/png",
    "js"        -> "application/javascript",
    "templates" -> "text/plain"
  )

  def pinTtl(ttl: Duration)        : WebBuilder = copy(pinTtl = ttl)
  def resourcePathPrefix(p: String): WebBuilder = copy(resourcePathPrefix = p)

  def apply(): ZipkinWeb = {
    val jsConfig = new JsConfig {
      override val pathPrefix = resourcePathPrefix
    }
    val cssConfig = new CssConfig {
      override val pathPrefix = resourcePathPrefix
    }
    val queryClient = queryClientBuilder.apply().build()
    val finagledClient = new gen.ZipkinQuery.FinagledClient(queryClient)
    val app = new App(rootUrl, pinTtl, jsConfig, cssConfig, finagledClient, serverBuilder.statsReceiver)

    val resource = new Resource(resourceDirs)

    new ZipkinWeb(app, resource, serverBuilder.serverPort, serverBuilder.tracerFactory)
  }
}
