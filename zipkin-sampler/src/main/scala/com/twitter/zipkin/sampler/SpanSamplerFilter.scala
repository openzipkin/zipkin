/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.sampler

import com.twitter.finagle.Service
import com.twitter.finagle.stats.{LoadedStatsReceiver, StatsReceiver}
import com.twitter.util.Future
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.SpanStore

/**
 * A sampling filter to be placed in front of a SpanStore. This takes a `sample` function that will
 * calculate whether the span should be sampled based on its traceId. If a span has its `debug` flag
 * set the function will be bypassed and the span stored regardless of the sampling rate.
 */
class SpanSamplerFilter(
  sample: Long => Boolean,
  stats: StatsReceiver = LoadedStatsReceiver.scope("SpanSamplerFilter")
) extends SpanStore.Filter {
  private[this] val DebugCounter = stats.counter("debugFlag")
  private[this] val DebugStats = stats.scope("debugFlag")

  def apply(spans: Seq[Span], store: Service[Seq[Span], Unit]): Future[Unit] =
    store(spans collect {
      case span if span.debug =>
        DebugCounter.incr()
        if (span.parentId.isEmpty && !span.serviceName.isEmpty)
          DebugStats.counter(span.serviceName.get).incr()
        span
      case span if sample(span.traceId) =>
        span
    })
}
