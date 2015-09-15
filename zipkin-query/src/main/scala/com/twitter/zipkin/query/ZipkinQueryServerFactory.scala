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

import java.net.InetSocketAddress

import com.twitter.app.App
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.finagle.{param, ListeningServer, ThriftMux}
import com.twitter.logging.Logger
import com.twitter.zipkin.storage.{DependencyStore, NullDependencyStore, SpanStore}
import com.twitter.zipkin.thriftscala.{DependencySource$FinagleService, ZipkinQuery$FinagleService}
import org.apache.thrift.protocol.TBinaryProtocol.Factory

trait ZipkinQueryServerFactory { self: App =>
  val queryServicePort = flag("zipkin.queryService.port", new InetSocketAddress(9411), "port for the query service to listen on")
  val queryServiceDurationBatchSize = flag("zipkin.queryService.durationBatchSize", 500, "max number of durations to pull per batch")

  def newQueryServer(
    spanStore: SpanStore,
    dependencyStore: DependencyStore = new NullDependencyStore,
    stats: StatsReceiver = DefaultStatsReceiver.scope("QueryService"),
    log: Logger = Logger.get("QueryService")
  ): ListeningServer = {
    val impl = new ThriftQueryService(spanStore, dependencyStore, queryServiceDurationBatchSize())
    ThriftMux.server
             .configured(param.Label("zipkin-query"))
             .serve(queryServicePort(), composeQueryService(impl, stats))
  }

  /**
   * Finagle+Scrooge doesn't yet support multiple interfaces on the same socket. This combines
   * ZipkinQuery and DependencySource$FinagleService until they do.
   */
  private def composeQueryService(impl: ThriftQueryService, stats: StatsReceiver) = {
    val protocolFactory = new Factory()
    val maxThriftBufferSize = ThriftMux.maxThriftBufferSize
    new ZipkinQuery$FinagleService(
      impl, protocolFactory, stats, maxThriftBufferSize
    ) {
      // Add functions from DependencySource until ThriftMux supports multiple interfaces on the
      // same port.
      functionMap ++= new DependencySource$FinagleService(
        impl, protocolFactory, stats, maxThriftBufferSize
      ) {
        val functions = functionMap // expose
      }.functions
    }
  }
}
