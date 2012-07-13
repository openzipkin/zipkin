package com.twitter.zipkin.adapter

import com.twitter.zipkin.common._
import com.twitter.zipkin.common.json.{JsonTraceSummary, JsonBinaryAnnotation, JsonSpan}

/**
 * Adapter to make common classes compatible with Jackson/Jerkson JSON generation
 */
object JsonAdapter extends Adapter {
  type annotationType = Annotation
  type annotationTypeType = AnnotationType
  type binaryAnnotationType = JsonBinaryAnnotation
  type endpointType = Endpoint
  type spanType = JsonSpan
  type traceSummaryType = JsonTraceSummary

  def apply(a: annotationType): Annotation = a
  //def apply(a: Annotation): annotationType = a

  def apply(a: annotationTypeType): AnnotationType = a
  //def apply(a: AnnotationType): annotationTypeType = a

  def apply(b: binaryAnnotationType): BinaryAnnotation = {
    throw new Exception("Not implemented")
  }

  def apply(b: BinaryAnnotation): binaryAnnotationType = {
    val value = b.annotationType match {
      case AnnotationType(0, _) => b.value.getInt              // bool
      case AnnotationType(1, _) => new String(b.value.array()) // bytes
      case AnnotationType(2, _) => b.value.getShort            // i16
      case AnnotationType(3, _) => b.value.getInt              // i32
      case AnnotationType(4, _) => b.value.getLong             // i64
      case AnnotationType(5, _) => b.value.getDouble           // double
      case AnnotationType(6, _) => new String(b.value.array()) // string
      case _ => {
        throw new Exception("Uh oh")
      }
    }
    JsonBinaryAnnotation(b.key, value, b.annotationType, b.host)
  }

  def apply(e: endpointType): Endpoint = e
  //def apply(e: Endpoint): endpointType = e

  def apply(s: spanType): Span = {
    Span(
      s.traceId.toLong,
      s.name,
      s.id.toLong,
      s.parentId.map(_.toLong),
      s.annotations,
      s.binaryAnnotations.map(JsonAdapter(_)))
  }

  def apply(s: Span): spanType = {
    JsonSpan(
      s.traceId.toString,
      s.name,
      s.id.toString,
      s.parentId.map(_.toString),
      s.serviceNames,
      s.firstAnnotation.get.timestamp,
      s.duration.get,
      s.annotations,
      s.binaryAnnotations.map(JsonAdapter(_)))
  }

  def apply(t: traceSummaryType): TraceSummary = {
    TraceSummary(t.traceId.toLong, t.startTimestamp, t.endTimestamp, t.durationMicro, t.serviceCounts, t.endpoints)
  }

  def apply(t: TraceSummary): traceSummaryType =  {
    JsonTraceSummary(t.traceId.toString, t.startTimestamp, t.endTimestamp, t.durationMicro, t.serviceCounts.toMap, t.endpoints)
  }
}


