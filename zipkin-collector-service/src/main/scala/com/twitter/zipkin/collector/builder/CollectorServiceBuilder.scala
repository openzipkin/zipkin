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

import java.net.InetSocketAddress

import com.twitter.finagle.ThriftMux
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{RuntimeEnvironment, ServiceTracker}
import com.twitter.zipkin.builder.{Builder, ZipkinServerBuilder}
import com.twitter.zipkin.collector.filter.{SamplerFilter, ServiceStatsFilter}
import com.twitter.zipkin.collector.sampler.AdjustableGlobalSampler
import com.twitter.zipkin.collector.{ScribeCollectorInterface, SpanReceiver, ZipkinCollector}
import com.twitter.zipkin.config.ConfigRequestHandler
import com.twitter.zipkin.config.sampler.{AdaptiveSamplerConfig, AdjustableRateConfig}
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.thriftscala._
import org.apache.thrift.protocol.TBinaryProtocol.Factory

/**
 * Immutable builder for ZipkinCollector
 *
 * CollectorInterface[T] stands up the actual server, and its filter is used to process the object
 * of type T into a Span in a worker thread.
 *
 * @tparam T type of object added to write queue
 */
case class CollectorServiceBuilder[T](
  storeBuilder: Builder[Store],
  receiver: Option[SpanReceiver.Processor => SpanReceiver] = None,
  scribeCategories: Set[String] = Set("zipkin"),
  sampleRateBuilder: Builder[AdjustableRateConfig] = Adjustable.local(1.0),
  adaptiveSamplerBuilder: Option[Builder[AdaptiveSamplerConfig]] = None,
  additionalConfigEndpoints: Seq[(String, Builder[AdjustableRateConfig])] = Seq.empty,
  queueMaxSize: Int = 500,
  queueNumWorkers: Int = 10,
  serverBuilder: ZipkinServerBuilder = ZipkinServerBuilder(9410, 9900)
) extends Builder[RuntimeEnvironment => ZipkinCollector] {

  val log = Logger.get()

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

  def sampleRate(c: Builder[AdjustableRateConfig]): CollectorServiceBuilder[T] = copy(sampleRateBuilder = c)
  def adaptiveSampler(b: Builder[AdaptiveSamplerConfig]) = copy(adaptiveSamplerBuilder = Some(b))
  def queueMaxSize(s: Int): CollectorServiceBuilder[T] = copy(queueMaxSize = s)
  def queueNumWorkers(w: Int): CollectorServiceBuilder[T] = copy(queueNumWorkers = w)

  def apply() = (runtime: RuntimeEnvironment) => {
    serverBuilder.apply().apply(runtime)

    log.info("Building store: %s".format(storeBuilder.toString))
    val store = storeBuilder.apply()

    val sampleRate = sampleRateBuilder.apply()
    val sampler = new SamplerFilter(new AdjustableGlobalSampler(sampleRate))

    import com.twitter.zipkin.conversions.thrift._

    val process = (spans: Seq[Span]) =>
      store.spanStore.apply(ServiceStatsFilter(sampler(spans.map(_.toSpan))))

    val stats = serverBuilder.statsReceiver
    val impl = new ScribeCollectorInterface(store, scribeCategories, process, stats)
    val server = ThriftMux.serve(
      new InetSocketAddress(serverBuilder.serverAddress, serverBuilder.serverPort),
      composeCollectorService(impl, stats))

    // initialize any alternate receiver, such as kafka
    val rcv = receiver.map(_(process))

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

    adaptiveSamplerBuilder foreach { builder =>
      val config = builder.apply()
      val service = config.apply()
      service.start()
      ServiceTracker.register(service)
    }

    new ZipkinCollector(server, store, rcv)
  }

  /**
   * Finagle+Scrooge doesn't yet support multiple interfaces on the same socket. This combines
   * Scribe and DependencySink until they do.
   */
  private def composeCollectorService(impl: ScribeCollectorInterface, stats: StatsReceiver) = {
    val protocolFactory = new Factory()
    val maxThriftBufferSize = ThriftMux.maxThriftBufferSize
    new Scribe$FinagleService(
      impl, protocolFactory, stats, maxThriftBufferSize
    ) {
      // Add functions from DependencySink until ThriftMux supports multiple interfaces on the
      // same port.
      functionMap ++= new DependencySink$FinagleService(
        impl, protocolFactory, stats, maxThriftBufferSize
      ) {
        val functions = functionMap // expose
      }.functions
    }
  }
}
