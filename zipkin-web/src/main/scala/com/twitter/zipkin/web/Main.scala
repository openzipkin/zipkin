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

import ch.qos.logback.classic.{Logger, Level}
import com.twitter.app.App
import com.twitter.finagle._
import com.twitter.finagle.httpx.{HttpMuxer, Request, Response}
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
import java.net.InetSocketAddress
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
  val webCacheResources = flag("zipkin.web.cacheResources", false, "cache static resources and mustache templates")
  val webResourcesRoot = flag("zipkin.web.resourcesRoot", "zipkin-web/src/main/resources", "on-disk location of resources")

  val queryDest = flag("zipkin.web.query.dest", "127.0.0.1:9411", "Location of the query server")
  val queryLimit = flag("zipkin.web.query.limit", 10, "Default query limit for trace results")
  val environment = flag("zipkin.web.environmentName", "", "The name of the environment Zipkin is running in")

  val logLevel = sys.env.get("WEB_LOG_LEVEL").getOrElse("INFO")
  LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    .asInstanceOf[Logger].setLevel(Level.toLevel(logLevel))

  // Eagerly parse the query destination as it is used in multiple places.
  queryDest.parse()

  /** If the span transport is set, trace accordingly, or disable tracing */
  DefaultTracer.self = sys.env.get("TRANSPORT_TYPE") match {
    case Some("scribe") => RawZipkinTracer(sys.env.get("SCRIBE_HOST").getOrElse("localhost"), sys.env.get("SCRIBE_PORT").getOrElse("1463").toInt)
    case Some("http") => new HttpZipkinTracer(queryDest(), DefaultStatsReceiver.get)
    case _ => NullTracer
  }

  /** Initialize a json-aware Finatra client, targeting the query host. */
  val queryClient = new HttpClient(
    httpService =
      Httpx.client.configured(param.Label("zipkin-query")).newClient(queryDest()).toService,
    defaultHeaders = Map("Host" -> queryDest()),
    mapper = new FinatraObjectMapper(ZipkinJson)
  )

  def newMustacheGenerator = new ZipkinMustache(webResourcesRoot(), webCacheResources())
  def newQueryExtractor = new QueryExtractor(queryLimit())
  def newHandlers = new Handlers(newMustacheGenerator, newQueryExtractor)

  def newWebServer(
    queryClient: HttpClient = queryClient,
    stats: StatsReceiver = DefaultStatsReceiver.scope("zipkin-web")
  ): Service[Request, Response] = {
    val handlers = newHandlers
    import handlers._

    val publicRoot = if (webCacheResources()) None else Some(webResourcesRoot())
    Seq(
      ("/app/", handlePublic(resourceDirs, typesMap, publicRoot)),
      ("/public/", handlePublic(resourceDirs, typesMap, publicRoot)),
      ("/", addLayout("Index", environment()) andThen handleIndex(queryClient)),
      ("/traces/:id", addLayout("Traces", environment()) andThen handleTraces(queryClient)),
      ("/dependency", addLayout("Dependency", environment()) andThen handleDependency()),
      ("/api/spans", handleRoute(queryClient, "/api/v1/spans")),
      ("/api/services", handleRoute(queryClient, "/api/v1/services")),
      ("/api/dependencies", handleRoute(queryClient, "/api/v1/dependencies"))
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
  def main() {
    /** Httpx.server will trace all paths. We don't care about static assets, so need to customize */
    val server = Httpx.Server(StackServer.newStack
      .replace(FilteredHttpEntrypointTraceInitializer.role, FilteredHttpEntrypointTraceInitializer))
      .configured(param.Label("zipkin-web"))
      .serve(webServerPort(), newWebServer(stats = statsReceiver.scope("zipkin-web")))
    onExit { server.close() }
    Await.ready(server)
  }
}
