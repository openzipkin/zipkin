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

import com.twitter.app.App
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.ThriftMux
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.logging.Logger
import com.twitter.util.Future
import com.twitter.zipkin.gen.Adjust
import com.twitter.zipkin.query.adjusters._
import com.twitter.zipkin.storage.{Aggregates, NullAggregates, SpanStore}

trait ZipkinQueryServerFactory { self: App =>
  val queryServicePort = flag("zipkin.queryService.port", ":9411", "port for the query service to listen on")
  val queryServiceDurationBatchSize = flag("zipkin.queryService.durationBatchSize", 500, "max number of durations to pull per batch")

  def newQueryServer(
    spanStore: SpanStore,
    aggregatesStore: Aggregates = new NullAggregates,
    adjusters: Map[Adjust, Adjuster] = constants.DefaultAdjusters,
    stats: StatsReceiver = DefaultStatsReceiver.scope("QueryService"),
    log: Logger = Logger.get("QueryService")
  ): ListeningServer = {
    ThriftMux.serveIface(queryServicePort(), new ThriftQueryService(
      spanStore, aggregatesStore, adjusters, queryServiceDurationBatchSize()))
  }
}
