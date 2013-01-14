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

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.finagle.tracing.Tracer
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{Service => OstrichService, ServiceTracker}
import com.twitter.util.Timer
import com.twitter.zipkin.collector.builder.CollectorInterface
import com.twitter.zipkin.collector.processor.ScribeFilter
import com.twitter.zipkin.collector.{ResilientZKNode, WriteQueue, ScribeCollectorService}
import com.twitter.zipkin.gen
import com.twitter.zipkin.storage.Store
import java.net.InetSocketAddress
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.zookeeper.KeeperException

object Scribe {

  /**
   * Builder for a Zipkin collector that exposes a Thrift Scribe interface as defined in zipkin-thrift
   */
  object Interface {
    def apply(categories: Set[String] = Set("zipkin")) = {
      type T = Seq[String]
      new CollectorInterface[T] {
        val filter = new ScribeFilter

        def apply() = (writeQueue: WriteQueue[T], stores: Seq[Store], address: InetSocketAddress, statsReceiver: StatsReceiver, tracerFactory: Tracer.Factory) => {

          Logger.get().info("Starting collector service on addr " + address)

          /* Start the service */
          val service = new ScribeCollectorService(writeQueue, stores, categories)
          service.start()
          ServiceTracker.register(service)

          /* Start the server */
          ServerBuilder()
            .codec(ThriftServerFramedCodec())
            .bindTo(address)
            .name("ZipkinCollector")
            .reportTo(statsReceiver)
            .tracerFactory(tracerFactory)
            .build(new gen.ZipkinCollector.FinagledService(service, new TBinaryProtocol.Factory()))
        }
      }
    }
  }

  /**
   * Builder for Service that registers Scribe-style server sets
   * @param zkClientBuilder
   * @param paths registers Scribe server sets at these paths in ZooKeeper
   */
  def serverSets(zkClientBuilder: ZooKeeperClientBuilder, paths: Set[String]) = new Builder[(InetSocketAddress, StatsReceiver, Timer) => OstrichService] {
    def apply() = (address: InetSocketAddress, statsReceiver: StatsReceiver, timer: Timer) => {
      new OstrichService {

        var zkNodes: Set[ResilientZKNode] = Set.empty

        def start() {
          val zkClient = zkClientBuilder.apply()
          zkNodes = paths map { path =>
            new ResilientZKNode(path, address.getHostName + ":" + address.getPort, zkClient, timer, statsReceiver)
          }
          zkNodes foreach { _.register() }
        }

        def shutdown() {
          try {
            zkNodes foreach { _.unregister() }
          } catch {
            case e: KeeperException => Logger.get().error("Could not unregister scribe zk node. Will continue shut down anyway", e)
          } finally {
            zkNodes = Set.empty
          }
        }
      }
    }
  }
}
