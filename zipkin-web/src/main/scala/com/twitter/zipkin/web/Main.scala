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

import java.net.InetSocketAddress

import ch.qos.logback.classic.{Level, Logger}
import com.twitter.app.App
import com.twitter.finagle._
import com.twitter.finagle.http.{HttpMuxer, Request, Response}
import com.twitter.finagle.server.StackServer
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.tracing.{DefaultTracer, NullTracer}
import com.twitter.finagle.zipkin.thrift.{HttpZipkinTracer, RawZipkinTracer}
import com.twitter.finatra.httpclient.HttpClient
import com.twitter.finatra.json.FinatraObjectMapper
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import com.twitter.zipkin.json.ZipkinJson
import com.twitter.zipkin.web.mustache.ZipkinMustache
import org.slf4j.LoggerFactory

trait ZipkinWebFactory { self: App =>
  private[this] val resourceDirs = Set(
    "/public/css",
    "/public/img",
    "/public/js",
    "/public/templates",

    "/app/libs",
    "/app/css",
    "/app/img",
    "/app/js"
  )

  private[this] val typesMap = Map(
    "css" -> "text/css",
    "png" -> "image/png",
    "js" -> "application/javascript"
  )

  val webServerPort = flag("zipkin.web.port", new InetSocketAddress(8080), "Listening port for the zipkin web frontend")
  val webRootUrl = flag("zipkin.web.rootUrl", "http://localhost:8080/", "Url where the service is located")
  val queryDest = flag("zipkin.web.query.dest", "127.0.0.1:9411", "Location of the query server")
  val queryLimit = flag("zipkin.web.query.limit", 10, "Default query limit for trace results")
  val environment = flag("zipkin.web.environmentName", "", "The name of the environment Zipkin is running in")

  val logLevel = sys.env.get("WEB_LOG_LEVEL").getOrElse("INFO")
  LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    .asInstanceOf[Logger].setLevel(Level.toLevel(logLevel))

 /**
  * Initialize a json-aware Finatra client, targeting the query host. Lazy to ensure
  * we get the host after the [[queryDest]] flag has been parsed.
  */
  lazy val queryClient = new HttpClient(
    httpService = Http.client.configured(param.Label("zipkin-query"))
                             .newClient(queryDest()).toService,
    defaultHeaders = Map(
      "Host" -> queryDest(),
      "Accept-Encoding" -> "gzip"
    ),
    mapper = new FinatraObjectMapper(ZipkinJson)
  )

  def newMustacheGenerator = new ZipkinMustache()
  def newQueryExtractor = new QueryExtractor(queryLimit())
  def newHandlers = new Handlers(newMustacheGenerator, newQueryExtractor)

  def newWebServer(
    queryClient: HttpClient = queryClient,
    stats: StatsReceiver = DefaultStatsReceiver.scope("zipkin-web")
  ): Service[Request, Response] = {
    val handlers = newHandlers
    import handlers._

    Seq(
      ("/app/", handlePublic(resourceDirs, typesMap)),
      ("/public/", handlePublic(resourceDirs, typesMap)),
      // In preparation of moving static assets to zipkin-query
      ("/api/v1/dependencies", handleRoute(queryClient, "/api/v1/dependencies")),
      ("/api/v1/services", handleRoute(queryClient, "/api/v1/services")),
      ("/api/v1/spans", handleRoute(queryClient, "/api/v1/spans")),
      ("/api/v1/trace/:id", handleTrace(queryClient)),
      ("/api/v1/traces", handleRoute(queryClient, "/api/v1/traces")),
      // TODO: Once the following are javascript-only, we can move remove zipkin-web
      ("/", addLayout("Index", environment()) andThen handleIndex(queryClient)),
      ("/traces/:id", addLayout("Traces", environment()) andThen handleTraces(queryClient)),
      ("/dependency", addLayout("Dependency", environment()) andThen handleDependency())
    ).foldLeft(new HttpMuxer) { case (m , (p, handler)) =>
      val path = p.split("/").toList
      val handlePath = path.takeWhile { t => !(t.startsWith(":") || t.startsWith("?:")) }
      val suffix = if (p.endsWith("/") || p.contains(":")) "/" else ""

      m.withHandler(handlePath.mkString("/") + suffix,
        collectStats(handlePath.foldLeft(stats) { case (s, p) => s.scope(p) }) andThen
        renderPage andThen
        catchExceptions andThen
        checkPath(path) andThen
        handler)
    }
  }
}

object Main extends TwitterServer with ZipkinWebFactory {

  /** If the span transport is set, trace accordingly, or disable tracing. */
  premain {
    DefaultTracer.self = sys.env.get("TRANSPORT_TYPE") match {
      case Some("scribe") => RawZipkinTracer(sys.env.get("SCRIBE_HOST").getOrElse("localhost"), sys.env.get("SCRIBE_PORT").getOrElse("1463").toInt)
      case Some("http") => new HttpZipkinTracer(queryDest(), DefaultStatsReceiver.get)
      case _ => NullTracer
    }
  }

  def main() = {
    BootstrapTrace.record("main")

    // Httpx.server will trace all paths. We don't care about static assets, so need to customize
    val server = Http.Server(StackServer.newStack
      .replace(FilteredHttpEntrypointTraceInitializer.role, FilteredHttpEntrypointTraceInitializer))
      .configured(param.Label("zipkin-web"))
      .serve(webServerPort(), newWebServer(stats = statsReceiver.scope("zipkin-web")))
    onExit { server.close() }

    BootstrapTrace.complete()

    // Note: this is blocking, so nothing after this will be called.
    Await.ready(server)
  }
}
