package com.twitter.zipkin.collector.builder

import com.twitter.finagle.builder.Server
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.tracing.Tracer
import com.twitter.finagle.Filter
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.collector.WriteQueue
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.Store
import java.net.InetSocketAddress

trait CollectorInterface[T]
  extends Builder[(WriteQueue[T], Seq[Store], InetSocketAddress, StatsReceiver, Tracer.Factory) => Server] {
  val filter: Filter[T, Unit, Span, Unit]
}
