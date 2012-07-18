package com.twitter.zipkin.common.json

case class JsonTraceCombo(trace: JsonTrace, traceSummary: Option[JsonTraceSummary], traceTimeline: Option[JsonTraceTimeline],
                          spanDepths: Option[Map[Long, Int]])
