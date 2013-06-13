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
package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.Endpoint
import com.twitter.zipkin.query.TraceSummary
import com.twitter.finagle.tracing.SpanId

case class JsonTraceSummary(traceId: String, startTimestamp: Long, endTimestamp: Long, durationMicro: Int,
                            serviceCounts: Map[String, Int], endpoints: List[Endpoint])
  extends WrappedJson

object JsonTraceSummary {
  def wrap(t: TraceSummary) =
    JsonTraceSummary(SpanId(t.traceId).toString, t.startTimestamp, t.endTimestamp, t.durationMicro, t.serviceCounts.toMap, t.endpoints)
}

