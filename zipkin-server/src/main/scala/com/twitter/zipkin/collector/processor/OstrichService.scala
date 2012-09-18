package com.twitter.zipkin.collector.processor

import com.twitter.finagle.Service
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.gen

class OstrichService(serviceStatsPrefix: String) extends Service[Span, Unit] {
  def apply(span: Span): Future[Unit] = {
    for {
      start <- span.getAnnotation(gen.Constants.SERVER_RECV)
      end <- span.getAnnotation(gen.Constants.SERVER_SEND)
    } {
      span.serviceNames.foreach(serviceName => {
        Stats.addMetric(serviceStatsPrefix + serviceName, (end - start).toInt)
        Stats.addMetric(serviceStatsPrefix + serviceName + "." + span.name, (end - start).toInt)
      })
    }

    Future.Unit
  }
}
