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
package com.twitter.zipkin.collector.filter

import com.twitter.ostrich.stats.Stats
import com.twitter.zipkin.common.Span

/**
 * Filter that increments a counter for each service present in the Span
 */
object ServiceStatsFilter {
  def apply(spans: Seq[Span]): Seq[Span] = spans.map { span =>
    span.serviceNames.foreach { name => Stats.incr("process_" + name) }
    span
  }
}
