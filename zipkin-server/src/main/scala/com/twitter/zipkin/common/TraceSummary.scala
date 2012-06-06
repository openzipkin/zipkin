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
package com.twitter.zipkin.common

import com.twitter.zipkin.gen
import scala.collection.Map

/**
 * Represents the summary of a trace.
 *
 * Instead of containing the full spans it has summed them up into
 * duration, endpoints involved and services involved.
 */
object TraceSummary {
  def fromThrift(summary: gen.TraceSummary): TraceSummary = {
    new TraceSummary(summary.traceId, summary.startTimestamp, summary.endTimestamp,
      summary.durationMicro, summary.serviceCounts,
      summary.endpoints.map(Endpoint.fromThrift(_)).toList)
  }

}

/**
 * @param traceId id of this trace
 * @param startTimestamp when did the trace start?
 * @param endTimestamp when did the trace end?
 * @param durationMicro how long did the traced operation take?
 * @param serviceCounts name of the services involved in the traced operation
 *                      mapped to the number of spans of that service
 * @param endpoints endpoints involved in the traced operation
 */
case class TraceSummary(traceId: Long, startTimestamp: Long, endTimestamp: Long,
                        durationMicro: Int, serviceCounts: Map[String, Int], endpoints: List[Endpoint]) {

  def toThrift: gen.TraceSummary = {
    gen.TraceSummary(traceId, startTimestamp, endTimestamp,
      durationMicro, serviceCounts, endpoints.map(e => e.toThrift))
  }
}
