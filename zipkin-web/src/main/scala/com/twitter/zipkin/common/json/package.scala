package com.twitter.zipkin.common

import com.twitter.zipkin.query._

package object json {

  trait JsonWrapper[T] {
    def wrap(orig: T): WrappedJson
  }

  /**
   * Indicates that the given subclass wraps a zipkin object in a more json-friendly manner
   */
  trait WrappedJson

  implicit def annotationToJson(a: Annotation) = JsonAnnotation.wrap(a)
  implicit def binaryAnnotationToJson(b: BinaryAnnotation) = JsonBinaryAnnotation.wrap(b)
  implicit def spanToJson(s: Span) = JsonSpan.wrap(s)
  implicit def timelineAnnotationToJson(t: TimelineAnnotation) = JsonTimelineAnnotation.wrap(t)
  implicit def traceToJson(t: Trace) = JsonTrace.wrap(t)
  implicit def traceTimelineToJson(t: TraceTimeline) = JsonTraceTimeline.wrap(t)
  implicit def traceSummaryToJson(t: TraceSummary) = JsonTraceSummary.wrap(t)
  implicit def traceComboToJson(t: TraceCombo) = JsonTraceCombo.wrap(t)
}