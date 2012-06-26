package com.twitter.zipkin.query

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
import com.twitter.logging.Logger
import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.zipkin.storage.{Aggregates, Index, Storage}
import com.twitter.zipkin.gen
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster
import com.twitter.finagle.builder.{ServerBuilder, Server}
import java.net.{InetAddress, InetSocketAddress}
import com.twitter.ostrich.admin.{ServiceTracker, Service}
import com.twitter.zipkin.config.ZipkinQueryConfig
import com.twitter.common.zookeeper.ServerSet

class ZipkinQuery(
  config: ZipkinQueryConfig, serverSet: ServerSet, storage: Storage, index: Index, aggregates: Aggregates
) extends Service {

  val log = Logger.get(getClass.getName)
  var thriftServer: Server = null

  val serverAddr = new InetSocketAddress(InetAddress.getLocalHost, config.serverPort)

  def start() {
    log.info("Starting query thrift service on addr " + serverAddr)
    val cluster = new ZookeeperServerSetCluster(serverSet)

    val queryService = new QueryService(storage, index, aggregates, config.adjusterMap)
    queryService.start()
    ServiceTracker.register(queryService)

    thriftServer = ServerBuilder()
      .codec(ThriftServerFramedCodec())
      .bindTo(serverAddr)
      .name("ZipkinQuery")
      .tracerFactory(config.tracerFactory)
      .build(new gen.ZipkinQuery.FinagledService(queryService, new TBinaryProtocol.Factory()))

    cluster.join(serverAddr)
  }

  def shutdown() {
    log.info("Shutting down query thrift service.")
    thriftServer.close()
    ServiceTracker.shutdown
  }
}


