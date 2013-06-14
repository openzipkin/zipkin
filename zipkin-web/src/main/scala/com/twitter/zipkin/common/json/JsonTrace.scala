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

import com.twitter.zipkin.query.Trace
import com.twitter.finagle.tracing.SpanId

case class JsonTrace(traceId: String, spans: Seq[JsonSpan], startTimestamp: Long, endTimestamp: Long, duration: Long, serviceCounts: Map[String, Int])
  extends WrappedJson

object JsonTrace {
  def wrap(t: Trace) = {
    /**
     *  TODO this is a pain in the ass, we need to fix common.Trace so the case class has the
     *  necessary fields in the constructor
     */
    val startAndEnd = t.getStartAndEndTimestamp.get
    JsonTrace(
      t.id map { SpanId(_).toString } getOrElse "",
      t.spans map { JsonSpan.wrap(_) },
      startAndEnd.start,
      startAndEnd.end,
      startAndEnd.end - startAndEnd.start,
      t.serviceCounts)
  }
}