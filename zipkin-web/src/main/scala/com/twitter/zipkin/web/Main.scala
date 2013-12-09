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
import com.twitter.finagle.http.HttpMuxer
import com.twitter.finagle.{Http, Thrift}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.gen.ZipkinQuery
import java.net.InetSocketAddress

object Main extends TwitterServer {
  import Handlers._

  private[this] val resourceDirs = Map(
    "/public/css"       -> "text/css",
    "/public/img"       -> "image/png",
    "/public/js"        -> "application/javascript",
    "/public/templates" -> "text/plain"
  )

  val serverPort = flag("zipkin.web.port", new InetSocketAddress(8080), "Listening port for the zipkin web frontend")

  val rootUrl = flag("zipkin.web.rootUrl", "http://localhost:8080/", "Url where the service is located")
  val pinTtl = flag("zipkin.web.pinTtl", 30.days, "Length of time pinned traces should exist")
  val resourcePathPrefix = flag("zipkin.web.resourcePathPrefix", "/public", "Path used for static resources")

  // TODO: make this idomatic
  val queryClientLocation = flag("zipkin.queryClient.location", "127.0.0.1:9411", "Location of the query server")

  def main() {
    // TODO: ThriftMux
    val queryClient = Thrift.newIface[ZipkinQuery.FutureIface]("ZipkinQuery=" + queryClientLocation())

    val muxer = Seq(
      ("/public/", handlePublic(resourceDirs)),
      ("/", addLayout(rootUrl()) andThen handleIndex(queryClient)),
      ("/traces/:id", addLayout(rootUrl()) andThen handleTraces),
      ("/static", addLayout(rootUrl()) andThen handleStatic),
      ("/aggregates", addLayout(rootUrl()) andThen handleAggregates),
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
      ("/api/pin/:id/:state", handleTogglePin(queryClient, pinTtl()))
    ).foldLeft(new HttpMuxer) { case (m , (p, handler)) =>
      val path = p.split("/").toList
      val handlePath = path.takeWhile { t => !(t.startsWith(":") || t.startsWith("?:")) }
      val suffix = if (p.endsWith("/") || p.contains(":")) "/" else ""

      m.withHandler(handlePath.mkString("/") + suffix,
        nettyToFinagle andThen
        renderPage andThen
        catchExceptions andThen
        checkPath(path) andThen
        handler)
    }

    val server = Http.serve(serverPort(), muxer)
    onExit { server.close() }
    Await.ready(server)
  }
}
