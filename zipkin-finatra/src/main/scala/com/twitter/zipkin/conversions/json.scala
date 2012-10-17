package com.twitter.zipkin.conversions

import com.twitter.zipkin.common.json.{JsonSpan, JsonBinaryAnnotation}
import com.twitter.zipkin.common.{Span, AnnotationType, BinaryAnnotation}

object json {

  /* BinaryAnnotation */
  class WrappedBinaryAnnotation(b: BinaryAnnotation) {
    lazy val toJson = {
      val value = b.annotationType match {
        case AnnotationType(0, _) => if (b.value.get() != 0) true else false  // bool
        case AnnotationType(1, _) => new String(b.value.array(), b.value.position(), b.value.remaining()) // bytes
        case AnnotationType(2, _) => b.value.getShort            // i16
        case AnnotationType(3, _) => b.value.getInt              // i32
        case AnnotationType(4, _) => b.value.getLong             // i64
        case AnnotationType(5, _) => b.value.getDouble           // double
        case AnnotationType(6, _) => new String(b.value.array(), b.value.position(), b.value.remaining()) // string
        case _ => {
          throw new Exception("Uh oh")
        }
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
        s.annotations.sortWith((a,b) => a.timestamp < b.timestamp),
        s.binaryAnnotations.map(_.toJson))
    }
  }
  implicit def spanToJson(s: Span) = new WrappedSpan(s)
}
