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
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.CommonFilters
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.finatra.json.modules.FinatraJacksonModule
import com.twitter.finatra.json.utils.CamelCasePropertyNamingStrategy
import com.twitter.finatra.logging.filter.{LoggingMDCFilter, TraceIdMDCFilter}
import com.twitter.finatra.logging.modules.Slf4jBridgeModule
import com.twitter.inject.TwitterModule
import com.twitter.zipkin.json.ZipkinJson
import com.twitter.zipkin.storage._

class ZipkinQueryServer(spanStore: SpanStore, dependencyStore: DependencyStore) extends HttpServer {

  val queryServiceDurationBatchSize = flag("zipkin.queryService.durationBatchSize", 500, "max number of durations to pull per batch")
  val queryLimit = flag("zipkin.queryService.limit", 10, "Default query limit for trace results")

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
}
