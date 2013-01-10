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
package com.twitter.zipkin.builder

import com.twitter.common.zookeeper.ServerSetImpl
import com.twitter.finagle.zookeeper.ZookeeperServerSetCluster
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.zipkin.gen
import com.twitter.zipkin.query.adjusters.{NullAdjuster, TimeSkewAdjuster, Adjuster}
import com.twitter.zipkin.query.ZipkinQuery
import com.twitter.zipkin.storage.Store
import java.net.InetSocketAddress

case class QueryServiceBuilder(
  storeBuilder: Builder[Store],
  serverSetPaths: List[(ZooKeeperClientBuilder, String)] = List.empty,
  serverBuilder: ZipkinServerBuilder = ZipkinServerBuilder(9411, 9901)
) extends Builder[RuntimeEnvironment => ZipkinQuery] {

  private val adjusterMap: Map[gen.Adjust, Adjuster] = Map (
    gen.Adjust.Nothing -> NullAdjuster,
    gen.Adjust.TimeSkew -> new TimeSkewAdjuster()
  )

  def addServerSetPath(p: (ZooKeeperClientBuilder, String)) = copy(serverSetPaths = serverSetPaths :+ p)

  def apply(): (RuntimeEnvironment) => ZipkinQuery = (runtime: RuntimeEnvironment) => {
    val log = Logger.get()
    serverBuilder.apply().apply(runtime)

    val address = new InetSocketAddress(serverBuilder.serverAddress, serverBuilder.serverPort)
    val store = storeBuilder.apply()

    /* Register server sets */
    serverSetPaths foreach { case (zkClientBuilder, path) =>
      log.info("Registering serverset: %s".format(path))
      val zkClient = zkClientBuilder.apply()
      val serverSet = new ServerSetImpl(zkClient, path)
      val cluster = new ZookeeperServerSetCluster(serverSet)
      cluster.join(address)
    }

    new ZipkinQuery(address, store.storage, store.index, store.aggregates, adjusterMap, serverBuilder.statsReceiver, serverBuilder.tracerFactory)
  }
}
