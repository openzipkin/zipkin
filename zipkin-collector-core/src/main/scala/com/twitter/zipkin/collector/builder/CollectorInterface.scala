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
