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
package com.twitter.zipkin.collector.builder

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.Service
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{ServiceTracker, RuntimeEnvironment, Service => OstrichService}
import com.twitter.util.Timer
import com.twitter.zipkin.builder.{ZipkinServerBuilder, Builder}
import com.twitter.zipkin.collector.filter.{ClientIndexFilter, ServiceStatsFilter, SamplerFilter}
import com.twitter.zipkin.collector.processor.{IndexService, StorageService, FanoutService}
import com.twitter.zipkin.collector.sampler.ZooKeeperGlobalSampler
import com.twitter.zipkin.collector.{WriteQueue, ZipkinCollector}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.config.sampler.{AdaptiveSamplerConfig, AdjustableRateConfig}
import com.twitter.zipkin.config.ConfigRequestHandler
import com.twitter.zipkin.storage.Store
import java.net.InetSocketAddress

/**
 * Immutable builder for ZipkinCollector
 *
 * CollectorInterface[T] stands up the actual server, and its filter is used to process the object
 * of type T into a Span in a worker thread.
 *
 * @param interface
 * @param storeBuilders
 * @param sampleRateBuilder
 * @param adaptiveSamplerBuilder
 * @param additionalConfigEndpoints
 * @param additionalServices
 * @param queueMaxSize
 * @param queueNumWorkers
 * @param serverBuilder
 * @tparam T type of object added to write queue
 */
case class CollectorServiceBuilder[T](
  interface: CollectorInterface[T],
  storeBuilders: Seq[Builder[Store]] = Seq.empty,
  sampleRateBuilder: Builder[AdjustableRateConfig] = Adjustable.local(1.0),
  adaptiveSamplerBuilder: Option[Builder[AdaptiveSamplerConfig]] = None,
  additionalConfigEndpoints: Seq[(String, Builder[AdjustableRateConfig])] = Seq.empty,
  additionalServices: Seq[Builder[(InetSocketAddress, StatsReceiver, Timer) => OstrichService]] = Seq.empty,
  queueMaxSize: Int = 500,
  queueNumWorkers: Int = 10,
  serverBuilder: ZipkinServerBuilder = ZipkinServerBuilder(9410, 9900)
) extends Builder[RuntimeEnvironment => ZipkinCollector] {

  val log = Logger.get()

  /**
   * Add a storage backend (Cassandra, Redis, etc.) to the builder
   * All Spans are stored and indexed in all the `Store`s given
   *
   * @param sb store builder
   * @return a new CollectorServiceBuilder
   */
  def writeTo(sb: Builder[Store]) =
    copy(storeBuilders = storeBuilders :+ sb)

  /**
   * Add a configuration endpoint to control `AdjustableRateConfig`s
   * Endpoints are available via
   *   GET  /config/<name>
   *   POST /config/<name>?value=<value>
   *
   * to get and set the values
   *
   * @param name name of the endpoint
   * @param builder AdjustableRateConfig builder
   * @return a new CollectorServiceBuilder
   */
  def addConfigEndpoint(name: String, builder: Builder[AdjustableRateConfig]) =
    copy(additionalConfigEndpoints = additionalConfigEndpoints :+ (name, builder))

  /**
   * Register builders for registering ServerSets
   *
   * @param s server set builder
   * @return a new CollectorServiceBuilder
   */
  def register(s: Builder[(InetSocketAddress, StatsReceiver, Timer) => OstrichService]) =
    copy(additionalServices = additionalServices :+ s)

  def sampleRate(c: Builder[AdjustableRateConfig]): CollectorServiceBuilder[T] = copy(sampleRateBuilder = c)
  def adaptiveSampler(b: Builder[AdaptiveSamplerConfig]) = copy(adaptiveSamplerBuilder = Some(b))
  def queueMaxSize(s: Int): CollectorServiceBuilder[T] = copy(queueMaxSize = s)
  def queueNumWorkers(w: Int): CollectorServiceBuilder[T] = copy(queueNumWorkers = w)

  def apply() = (runtime: RuntimeEnvironment) => {
    serverBuilder.apply().apply(runtime)

    log.info("Building %d stores: %s".format(storeBuilders.length, storeBuilders.toString))
    val stores = storeBuilders map {
      _.apply()
    }
    val storeProcessors = stores flatMap { store =>
      Seq(new StorageService(store.storage), new ClientIndexFilter andThen new IndexService(store.index))
    }

    val sampleRate = sampleRateBuilder.apply()

    val processor: Service[T, Unit] = {
      interface.filter andThen
      new SamplerFilter(new ZooKeeperGlobalSampler(sampleRate)) andThen
      new ServiceStatsFilter andThen
      new FanoutService[Span](storeProcessors)
    }

    val queue = new WriteQueue(queueMaxSize, queueNumWorkers, processor)
    queue.start()

    val server = interface.apply().apply(queue,
      stores,
      new InetSocketAddress(serverBuilder.serverAddress, serverBuilder.serverPort),
      serverBuilder.statsReceiver,
      serverBuilder.tracer)

    /**
     * Add config endpoints with the sampleRate endpoint. Available via:
     *   GET  /config/<name>
     *   POST /config/<name>?value=0.2
     */
    val configEndpoints = ("/config/sampleRate", sampleRate) +: additionalConfigEndpoints.map { case (path, builder) =>
      ("/config/%s".format(path), builder.apply())
    }
    configEndpoints foreach { case (path, adjustable) =>
      serverBuilder.adminHttpService map { _.addContext(path, new ConfigRequestHandler(adjustable)) }
    }

    /* Start additional services (server sets) */
    additionalServices foreach { builder =>
      val s = builder.apply().apply(serverBuilder.socketAddress, serverBuilder.statsReceiver, serverBuilder.timer)
      s.start()
      ServiceTracker.register(s)
    }

    adaptiveSamplerBuilder foreach { builder =>
      val config = builder.apply()
      val service = config.apply()
      service.start()
      ServiceTracker.register(service)
    }

    new ZipkinCollector(server)
  }
}
