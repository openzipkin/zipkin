package com.twitter.zipkin.query

import com.twitter.zipkin.common.TraceSummary

object TraceCombo {
  def apply(trace: Trace) {
    TraceCombo(trace, TraceSummary(trace), TraceTimeline(trace), trace.toSpanDepths)
  }
}

case class TraceCombo(trace: Trace, traceSummary: Option[TraceSummary], traceTimeline: Option[TraceTimeline],
                     spanDepths: Option[Map[Long, Int]])
