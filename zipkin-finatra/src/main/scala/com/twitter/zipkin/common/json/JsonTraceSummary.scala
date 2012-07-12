package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.{Endpoint, TraceSummary}

object JsonTraceSummary {
  def apply(t: TraceSummary): JsonTraceSummary =
    JsonTraceSummary(t.traceId.toString, t.startTimestamp, t.endTimestamp, t.durationMicro, t.serviceCounts.toMap, t.endpoints)
}
case class JsonTraceSummary(traceId: String, startTimestamp: Long, endTimestamp: Long, durationMicro: Int,
                            serviceCounts: Map[String, Int], endpoints: List[Endpoint])
