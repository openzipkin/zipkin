package com.twitter.zipkin.common.json

case class JsonTrace(traceId: String, spans: Seq[JsonSpan], startTimestamp: Long, endTimestamp: Long, duration: Long, serviceCounts: Map[String, Int])
