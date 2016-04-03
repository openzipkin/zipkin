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
package com.twitter.zipkin.query

import com.google.inject.Provides
import com.twitter.conversions.time._
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.{ListeningServer, param, Http}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.server.StackServer
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{TraceIdMDCFilter, LoggingMDCFilter, CommonFilters}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.finatra.json.modules.FinatraJacksonModule
import com.twitter.finatra.json.utils.CamelCasePropertyNamingStrategy
import com.twitter.finatra.logging.modules.Slf4jBridgeModule
import com.twitter.inject.TwitterModule
import com.twitter.inject.server.PortUtils
import com.twitter.util.Await
import com.twitter.zipkin.json.ZipkinJson
import com.twitter.zipkin.storage._

class ZipkinQueryServer(spanStore: SpanStore, dependencyStore: DependencyStore) extends HttpServer {

  // Bind flags used with javax.Inject
  flag("zipkin.queryService.durationBatchSize", 500, "max number of durations to pull per batch")
  flag("zipkin.queryService.limit", 10, "Default query limit for trace results")
  flag("zipkin.queryService.lookback", 7.days.inMillis, "Default query lookback for trace results, in milliseconds")
  flag("zipkin.queryService.servicesMaxAge", 5*60, "Get services cache TTL")
  flag("zipkin.queryService.environment", "", "Name of the environment this Zipkin server is running in")

  object StorageModule extends TwitterModule {
    @Provides
    def provideSpanStore() = spanStore

    @Provides
    def provideDependencyStore() = dependencyStore
  }

  override protected def jacksonModule = new FinatraJacksonModule {
    override protected def additionalJacksonModules = Seq(ZipkinJson.module)
    // don't convert to snake case, as the rest of zipkin expects lower-camel
    override protected val propertyNamingStrategy = CamelCasePropertyNamingStrategy
  }

  override def modules = Seq(Slf4jBridgeModule, StorageModule)

  override def configureHttp(router: HttpRouter) {
    router
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .filter[CommonFilters]
      .add[ZipkinQueryController]
  }

  // All of the below is needed to do disable POST tracing:
  // https://github.com/twitter/finatra/issues/271
  private var httpServer: ListeningServer = _

  override def postWarmup() {
    if (disableAdminHttpServer) {
      info("Disabling the Admin HTTP Server since disableAdminHttpServer=true")
      adminHttpServer.close()
    }
    /** Httpx.server will trace all paths. Disable tracing of POST. */
    httpServer = Http.Server(StackServer.newStack
      .replace(FilteredHttpEntrypointTraceInitializer.role, FilteredHttpEntrypointTraceInitializer))
      .configured(param.Label("zipkin-query"))
      .configured(param.Stats(injector.instance[StatsReceiver]))
      .serve(defaultFinatraHttpPort, httpService)
    info("http server started on port: " + httpExternalPort.get)

    onExit {
      Await.result(httpServer.close(defaultShutdownTimeout.fromNow))
    }
  }

  override def httpExternalPort = Option(httpServer).map(PortUtils.getPort)

  override def waitForServer() = Await.ready(httpServer)

}
