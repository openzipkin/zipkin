package com.twitter.zipkin.collector.processor

import com.twitter.finagle.{TooManyWaitersException, Service}
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.Storage

class StorageService(storage: Storage) extends Service[Span, Unit] {

  private[this] val log = Logger.get()

  def apply(span: Span): Future[Unit] = {
    storage.storeSpan(span) onFailure {
      case t: TooManyWaitersException =>
      case e => {
        Stats.getCounter("exception_%s_%s".format("storeSpan", e.getClass)).incr()
        log.error(e, "storeSpan")
      }
    }
  }

  override def release() {
    storage.close()
  }
}
