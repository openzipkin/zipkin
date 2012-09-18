package com.twitter.zipkin.collector.processor

import com.twitter.finagle.{Service, Filter}
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.twitter.zipkin.collector.sampler.GlobalSampler
import com.twitter.zipkin.common.Span

class SamplerFilter(sampler: GlobalSampler) extends Filter[Span, Unit, Span, Unit] {
  def apply(span: Span, service: Service[Span, Unit]): Future[Unit] = {
    span.serviceNames.foreach { name => Stats.incr("received_" + name) }

    /**
     * If the span was created with debug mode on we guarantee that it will be
     * stored no matter what our sampler tells us
     */
    if (span.debug) {
      Stats.incr("debugflag")
      service(span)
    } else if (sampler(span.traceId)) {
      service(span)
    } else {
      Future.Unit
    }
  }
}
