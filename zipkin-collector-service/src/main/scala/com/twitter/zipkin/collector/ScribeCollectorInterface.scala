package com.twitter.zipkin.collector

import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.receiver.scribe.ScribeReceiver
import com.twitter.zipkin.storage.Store
import com.twitter.zipkin.thriftscala
import com.twitter.zipkin.thriftscala.LogEntry

class ScribeCollectorInterface(
  store: Store,
  categories: Set[String],
  process: Seq[thriftscala.Span] => Future[Unit],
  stats: StatsReceiver = DefaultStatsReceiver.scope("ScribeReceiver")
) extends thriftscala.Scribe.FutureIface with thriftscala.DependencyStore.FutureIface {

  lazy val receiver = new ScribeReceiver(categories, process, stats)

  private val log = Logger.get

  override def log(messages: Seq[LogEntry]) = receiver.log(messages)

  override def storeDependencies(dependencies: thriftscala.Dependencies) = {
    Stats.timeFutureMillisLazy("collector.storeDependencies") {
      store.dependencies.storeDependencies(dependencies.toDependencies)
    } rescue {
      case e: Exception =>
        log.error(e, "storeDependencies failed")
        Stats.incr("collector.storeDependencies")
        Future.exception(thriftscala.DependenciesException(e.toString))
    }
  }

  override def getDependencies(startTime: Option[Long], endTime: Option[Long]) = {
    store.dependencies.getDependencies(startTime, endTime).map(_.toThrift)
  }
}
