package com.twitter.zipkin.adapter

import com.twitter.zipkin.common.Trace
import com.twitter.zipkin.common.json.{JsonTimelineAnnotation, JsonTrace, JsonTraceCombo, JsonTraceTimeline}
import com.twitter.zipkin.query.{TraceCombo, TraceTimeline, TimelineAnnotation}

/**
 * JS doesn't like Longs so we need to convert them to strings
 */
object JsonQueryAdapter extends QueryAdapter {
  type timelineAnnotationType = JsonTimelineAnnotation
  type traceTimelineType = JsonTraceTimeline
  type traceComboType = JsonTraceCombo
  type traceType = JsonTrace

  /* no change between json and common */
  def apply(t: timelineAnnotationType): TimelineAnnotation = {
    TimelineAnnotation(t.timestamp, t.value, t.host, t.spanId.toLong, t.parentId.map(_.toLong), t.serviceName, t.spanName)
  }

  def apply(t: TimelineAnnotation): timelineAnnotationType = {
    JsonTimelineAnnotation(t.timestamp, t.value, t.host, t.spanId.toString, t.parentId.map(_.toString), t.serviceName, t.spanName)
  }

  /* json to common */
  def apply(t: traceTimelineType): TraceTimeline = {
    TraceTimeline(
      t.traceId.toLong,
      t.rootSpanId.toLong,
      t.annotations.map(JsonQueryAdapter(_)),
      t.binaryAnnotations.map(JsonAdapter(_)))
  }

  /* common to json */
  def apply(t: TraceTimeline): traceTimelineType = {
    JsonTraceTimeline(
      t.traceId.toString,
      t.rootSpanId.toString,
      t.annotations.map(JsonQueryAdapter(_)),
      t.binaryAnnotations.map(JsonAdapter(_)))
  }

  /* json to common */
  def apply(t: traceComboType): TraceCombo = {
    TraceCombo(
      JsonQueryAdapter(t.trace),
      t.traceSummary.map(JsonAdapter(_)),
      t.traceTimeline.map(JsonQueryAdapter(_)),
      t.spanDepths)
  }

  /* common to json */
  def apply(t: TraceCombo): traceComboType = {
    JsonTraceCombo(
      JsonQueryAdapter(t.trace),
      t.traceSummary.map(JsonAdapter(_)),
      t.traceTimeline.map(JsonQueryAdapter(_)),
      t.spanDepths)
  }

  /* json to common */
  def apply(t: traceType): Trace = {
    throw new Exception("NOT IMPLEMENTED")
  }

  /* common to json */
  def apply(t: Trace): traceType = {
    /**
     *  TODO this is a pain in the ass, we need to fix common.Trace so the case class has the
     *  necessary fields in the constructor
     */
    val startAndEnd = t.getStartAndEndTimestamp.get
    JsonTrace(
      t.id.map(_.toString).getOrElse(""),
      t.mergeSpans.spans.map(JsonAdapter(_)),
      startAndEnd.start,
      startAndEnd.end,
      startAndEnd.end - startAndEnd.start,
      t.serviceCounts)
  }
}
