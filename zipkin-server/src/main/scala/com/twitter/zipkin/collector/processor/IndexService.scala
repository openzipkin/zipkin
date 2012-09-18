package com.twitter.zipkin.collector.processor

import com.twitter.finagle.{TooManyWaitersException, Service}
import com.twitter.logging.Logger
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.twitter.zipkin.storage.Index
import com.twitter.zipkin.collector.filter.IndexingFilter
import com.twitter.zipkin.common.Span

class IndexService(index: Index, indexFilter: IndexingFilter) extends Service[Span, Unit] {
  private[this] val log = Logger.get()

  def apply(span: Span): Future[Unit] = {
    if (indexFilter.shouldIndex(span)) {
      Future.join(Seq {
        index.indexTraceIdByServiceAndName(span) onFailure failureHandler("indexTraceIdByServiceAndName")
        index.indexSpanByAnnotations(span)       onFailure failureHandler("indexSpanByAnnotations")
        index.indexServiceName(span)             onFailure failureHandler("indexServiceName")
        index.indexSpanNameByService(span)       onFailure failureHandler("indexSpanNameByService")
        index.indexSpanDuration(span)            onFailure failureHandler("indexSpanDuration")
      })
    } else {
      Future.Unit
    }
  }

  protected def failureHandler(method: String): (Throwable) => Unit = {
    case t: TooManyWaitersException =>
    case e => {
      Stats.getCounter("exception_%s_%s".format(method, e.getClass)).incr()
      log.error(e, method)
    }
  }
}
