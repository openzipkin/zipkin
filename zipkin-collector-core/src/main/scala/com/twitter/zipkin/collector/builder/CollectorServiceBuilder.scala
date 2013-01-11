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
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import com.twitter.zipkin.config.ConfigRequestHandler
import com.twitter.zipkin.storage.Store
import java.net.InetSocketAddress

case class CollectorServiceBuilder[T](
  interface: CollectorInterface[T],
  storeBuilders: Seq[Builder[Store]] = Seq.empty,
  sampleRateBuilder: Builder[AdjustableRateConfig] = Adjustable.local(1.0),
  additionalConfigEndpoints: Seq[(String, Builder[AdjustableRateConfig])] = Seq.empty,
  additionalServices: Seq[Builder[(InetSocketAddress, StatsReceiver, Timer) => OstrichService]] = Seq.empty,
  queueMaxSize: Int = 500,
  queueNumWorkers: Int = 10,
  serverBuilder: ZipkinServerBuilder = ZipkinServerBuilder(9410, 9900)
) extends Builder[RuntimeEnvironment => ZipkinCollector] {

  val log = Logger.get()

  def writeTo(sb: Builder[Store]) = copy(storeBuilders = storeBuilders :+ sb)
  def addConfigEndpoint(name: String, builder: Builder[AdjustableRateConfig]) = copy(additionalConfigEndpoints = additionalConfigEndpoints :+ (name, builder))
  def register(s: Builder[(InetSocketAddress, StatsReceiver, Timer) => OstrichService]) = copy(additionalServices = additionalServices :+ s)

  def sampleRate(c: Builder[AdjustableRateConfig]): CollectorServiceBuilder[T] = copy(sampleRateBuilder = c)
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
    ServiceTracker.register(queue)

    val server = interface.apply().apply(queue,
      stores,
      new InetSocketAddress(serverBuilder.serverAddress, serverBuilder.serverPort),
      serverBuilder.statsReceiver,
      serverBuilder.tracerFactory)

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

    new ZipkinCollector(server)
  }
}
