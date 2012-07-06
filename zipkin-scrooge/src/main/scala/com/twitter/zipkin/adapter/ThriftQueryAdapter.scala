package com.twitter.zipkin.adapter

import com.twitter.zipkin.gen
import com.twitter.zipkin.query.{TraceTimeline, TimelineAnnotation}

object ThriftQueryAdapter extends QueryAdapter {
  type timelineAnnotationType = gen.TimelineAnnotation
  type traceTimelineType = gen.TraceTimeline

  /* TimelineAnnotation from Thrift */
  def apply(t: timelineAnnotationType): TimelineAnnotation = {
    TimelineAnnotation(
      t.`timestamp`,
      t.`value`,
      ThriftAdapter(t.`host`),
      t.`spanId`,
      t.`parentId`,
      t.`serviceName`,
      t.`spanName`)
  }

  /* TimelineAnnotation to Thrift */
  def apply(t: TimelineAnnotation): timelineAnnotationType = {
    gen.TimelineAnnotation(
      t.timestamp,
      t.value,
      ThriftAdapter(t.host),
      t.spanId,
      t.parentId,
      t.serviceName,
      t.spanName)
  }

  /* TraceTimeline from Thrift */
  def apply(t: traceTimelineType): TraceTimeline = {
    TraceTimeline(
      t.`traceId`,
      t.`rootMostSpanId`,
      t.`annotations`.map { ThriftQueryAdapter(_) },
      t.`binaryAnnotations`.map { ThriftAdapter(_) })
  }

  /* TraceTimeline to Thrift */
  def apply(t: TraceTimeline): traceTimelineType = {
    gen.TraceTimeline(
      t.traceId,
      t.rootSpanId,
      t.annotations.map { ThriftQueryAdapter(_) },
      t.binaryAnnotations.map { ThriftAdapter(_) })
  }
}
