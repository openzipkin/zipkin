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

import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.{Http, Service, Thrift}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.common.json.ZipkinJson
import com.twitter.zipkin.common.mustache.ZipkinMustache
import com.twitter.zipkin.gen.ZipkinQuery
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}

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
  val webPinTtl = flag("zipkin.web.pinTtl", 30.days, "Length of time pinned traces should exist")

  val queryDest = flag("zipkin.web.query.dest", "127.0.0.1:9411", "Location of the query server")
  def newQueryClient(): ZipkinQuery[Future] =
    Thrift.newIface[ZipkinQuery[Future]]("ZipkinQuery=" + queryDest())

  def newWebServer(
    queryClient: ZipkinQuery[Future] = newQueryClient(),
    stats: StatsReceiver = DefaultStatsReceiver.scope("zipkin-web")
  ): Service[HttpRequest, HttpResponse] = {
    val jsonGenerator = new ZipkinJson
    val mustacheGenerator = new ZipkinMustache(webResourcesRoot(), webCacheResources())
    val handlers = new Handlers(jsonGenerator, mustacheGenerator)
    import handlers._

    val publicRoot = if (webCacheResources()) None else Some(webResourcesRoot())
    Seq(
      ("/app/", handlePublic(resourceDirs, typesMap, publicRoot)),
      ("/public/", handlePublic(resourceDirs, typesMap, publicRoot)),
      ("/", addLayout andThen handleIndex(queryClient)),
      ("/traces/:id", addLayout andThen handleTraces(queryClient)),
      ("/realtime", addLayout andThen handleRealtime(queryClient)),
      ("/api/query", handleQuery(queryClient)),
      ("/api/services", handleServices(queryClient)),
      ("/api/spans", requireServiceName andThen handleSpans(queryClient)),
      ("/api/top_annotations", requireServiceName andThen handleTopAnnotations(queryClient)),
      ("/api/top_kv_annotations", requireServiceName andThen handleTopKVAnnotations(queryClient)),
      ("/api/dependencies", handleDependencies(queryClient)),
      ("/api/dependencies/?:startTime/?:endTime", handleDependencies(queryClient)),
      ("/api/get/:id", handleGetTrace(queryClient)),
      ("/api/trace/:id", handleGetTrace(queryClient)),
      ("/api/is_pinned/:id", handleIsPinned(queryClient)),
      ("/api/pin/:id/:state", handleTogglePin(queryClient, webPinTtl()))
    ).foldLeft(new HttpMuxer) { case (m , (p, handler)) =>
      val path = p.split("/").toList
      val handlePath = path.takeWhile { t => !(t.startsWith(":") || t.startsWith("?:")) }
      val suffix = if (p.endsWith("/") || p.contains(":")) "/" else ""

      m.withHandler(handlePath.mkString("/") + suffix,
        nettyToFinagle andThen
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
    val server = Http.serve(webServerPort(), newWebServer(stats = statsReceiver.scope("zipkin-web")))
    onExit { server.close() }
    Await.ready(server)
  }
}
