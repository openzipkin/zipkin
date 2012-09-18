package com.twitter.zipkin.collector.processor

import com.twitter.finagle.Service
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.twitter.zipkin.common.Span

class StatsService extends Service[Span, Unit] {
  def apply(span: Span): Future[Unit] = {
    span.serviceNames.foreach { name => Stats.incr("process_" + name) }
    Future.Unit
  }
}
