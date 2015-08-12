/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.query

import com.twitter.zipkin.query.adjusters.Adjuster
import com.twitter.finagle.builder.{ServerBuilder, Server}
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.finagle.tracing.{NullTracer, Tracer}
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{ServiceTracker, Service}
import com.twitter.zipkin.thriftscala
import com.twitter.zipkin.storage.{Aggregates, Index, Storage}
import java.net.InetSocketAddress
import org.apache.thrift.protocol.TBinaryProtocol

class ZipkinQuery(
  serverAddress: InetSocketAddress,
  storage: Storage,
  index: Index,
  aggregates: Aggregates,
  adjusterMap: Map[thriftscala.Adjust, Adjuster] = Map.empty,
  statsReceiver: StatsReceiver = NullStatsReceiver,
  tracer: Tracer = NullTracer
) extends Service {

  val log = Logger.get(getClass.getName)
  var thriftServer: Server = null

  def start() {
    log.info("Starting query thrift service on addr " + serverAddress)

    val queryService = new QueryService(storage, index, aggregates, adjusterMap, statsReceiver)
    queryService.start()
    ServiceTracker.register(queryService)

    thriftServer = ServerBuilder()
      .codec(ThriftServerFramedCodec())
      .bindTo(serverAddress)
      .name("ZipkinQuery")
      .tracer(tracer)
      .build(new thriftscala.ZipkinQuery.FinagledService(queryService, new TBinaryProtocol.Factory()))
  }

  def shutdown() {
    log.info("Shutting down query thrift service.")
    thriftServer.close()
  }
}


