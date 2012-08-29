package com.twitter.zipkin.adapter

import com.twitter.zipkin.common.json._
import com.twitter.zipkin.query._

/**
 * JS doesn't like Longs so we need to convert them to strings
 */
object JsonQueryAdapter extends QueryAdapter {
  type timelineAnnotationType = JsonTimelineAnnotation
  type traceTimelineType = JsonTraceTimeline
  type traceComboType = JsonTraceCombo
  type traceSummaryType = JsonTraceSummary
  type traceType = JsonTrace

  type queryAnnotationType = QueryAnnotation
  type queryRequestType    = QueryRequest
  type queryResponseType   = QueryResponse

  type orderType           = Order

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
      t.traceSummary.map(JsonQueryAdapter(_)),
      t.traceTimeline.map(JsonQueryAdapter(_)),
      t.spanDepths)
  }

  /* common to json */
  def apply(t: TraceCombo): traceComboType = {
    JsonTraceCombo(
      JsonQueryAdapter(t.trace),
      t.traceSummary.map(JsonQueryAdapter(_)),
      t.traceTimeline.map(JsonQueryAdapter(_)),
      t.spanDepths)
  }

  def apply(t: traceSummaryType): TraceSummary = {
    TraceSummary(t.traceId.toLong, t.startTimestamp, t.endTimestamp, t.durationMicro, t.serviceCounts, t.endpoints)
  }

  def apply(t: TraceSummary): traceSummaryType =  {
    JsonTraceSummary(t.traceId.toString, t.startTimestamp, t.endTimestamp, t.durationMicro, t.serviceCounts.toMap, t.endpoints)
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
      t.spans.map(JsonAdapter(_)),
      startAndEnd.start,
      startAndEnd.end,
      startAndEnd.end - startAndEnd.start,
      t.serviceCounts)
  }

  /* No-ops since not used */
  def apply(q: queryAnnotationType): QueryAnnotation = q
  //def apply(q: QueryAnnotation): queryAnnotationType = q

  def apply(q: queryRequestType): QueryRequest = q
  //def apply(q: QueryRequest): queryRequestType = q

  def apply(q: queryResponseType): QueryResponse = q
  //def apply(q: QueryResponse): queryResponseType = q

  def apply(o: orderType): Order = o
  //def apply(o: Order): orderType = o
}
