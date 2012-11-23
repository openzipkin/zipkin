package com.twitter.zipkin.conversions

import com.twitter.zipkin.common.json._
import com.twitter.zipkin.common._
import com.twitter.zipkin.query._

/**
 * json doesn't like Longs, so we need to convert them to strings
 */
object json {

  /* Annotation */
  class WrappedAnnotation(a: Annotation) {
    lazy val toJson = {
      JsonAnnotation(a.timestamp.toString, a.value, a.host, a.duration map { _.inMicroseconds.toString })
    }
  }
  implicit def annotationToJson(a: Annotation) = new WrappedAnnotation(a)

  /* BinaryAnnotation */
  class WrappedBinaryAnnotation(b: BinaryAnnotation) {
    lazy val toJson = {
      val value = try {
        b.annotationType match {
          case AnnotationType(0, _) => if (b.value.get() != 0) true else false  // bool
          case AnnotationType(1, _) => new String(b.value.array(), b.value.position(), b.value.remaining()) // bytes
          case AnnotationType(2, _) => b.value.getShort            // i16
          case AnnotationType(3, _) => b.value.getInt              // i32
          case AnnotationType(4, _) => b.value.getLong             // i64
          case AnnotationType(5, _) => b.value.getDouble           // double
          case AnnotationType(6, _) => new String(b.value.array(), b.value.position(), b.value.remaining()) // string
          case _ => {
            throw new Exception("Unsupported annotation type: %s".format(b))
          }
        }
      } catch {
        case e: Exception => "Error parsing binary annotation"
      }
      JsonBinaryAnnotation(b.key, value, b.annotationType, b.host)
    }
  }
  implicit def binaryAnnotationToJson(b: BinaryAnnotation) = new WrappedBinaryAnnotation(b)

  /* Span */
  class WrappedSpan(s: Span) {
    lazy val toJson = {
      JsonSpan(
        s.traceId.toString,
        s.name,
        s.id.toString,
        s.parentId.map(_.toString),
        s.serviceNames,
        s.firstAnnotation.map(_.timestamp),
        s.duration,
        s.annotations.sortWith((a,b) => a.timestamp < b.timestamp).map { _.toJson },
        s.binaryAnnotations.map(_.toJson))
    }
  }
  implicit def spanToJson(s: Span) = new WrappedSpan(s)

  /* TimelineAnnotation */
  class WrappedTimelineAnnotation(t: TimelineAnnotation) {
    lazy val toJson = {
      JsonTimelineAnnotation(t.timestamp.toString, t.value, t.host, t.spanId.toString, t.parentId map { _.toString }, t.serviceName, t.spanName)
    }
  }
  implicit def timelineAnnotationToJson(t: TimelineAnnotation) = new WrappedTimelineAnnotation(t)

  /* Trace */
  class WrappedTrace(t: Trace) {
    lazy val toJson = {
      /**
       *  TODO this is a pain in the ass, we need to fix common.Trace so the case class has the
       *  necessary fields in the constructor
       */
      val startAndEnd = t.getStartAndEndTimestamp.get
      JsonTrace(
        t.id map { _.toString } getOrElse "",
        t.spans map { _.toJson },
        startAndEnd.start,
        startAndEnd.end,
        startAndEnd.end - startAndEnd.start,
        t.serviceCounts)
    }
  }
  implicit def traceToJson(t: Trace) = new WrappedTrace(t)

  /* TraceTimeline */
  class WrappedTraceTimeline(t: TraceTimeline) {
    lazy val toJson = {
      JsonTraceTimeline(t.traceId.toString, t.rootSpanId.toString, t.annotations map { _.toJson }, t.binaryAnnotations map { _.toJson })
    }
  }
  implicit def traceTimelineToJson(t: TraceTimeline) = new WrappedTraceTimeline(t)

  /* TraceSummary */
  class WrappedTraceSummary(t: TraceSummary) {
    lazy val toJson = {
      JsonTraceSummary(t.traceId.toString, t.startTimestamp, t.endTimestamp, t.durationMicro, t.serviceCounts.toMap, t.endpoints)
    }
  }
  implicit def traceSummaryToJson(t: TraceSummary) = new WrappedTraceSummary(t)

  /* TraceCombo */
  class WrappedTraceCombo(t: TraceCombo) {
    lazy val toJson = {
      JsonTraceCombo(t.trace.toJson, t.traceSummary map { _.toJson }, t.traceTimeline map { _.toJson }, t.spanDepths)
    }
  }
  implicit def traceComboToJson(t: TraceCombo) = new WrappedTraceCombo(t)
}
