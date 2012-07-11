package com.twitter.zipkin.web

import com.twitter.zipkin.adapter.Adapter
import com.twitter.zipkin.common._

/**
 * Adapter to make common classes compatible with Jackson/Jerkson JSON generation
 */
object JsonAdapter extends Adapter {
  type annotationType        = Annotation
  type annotationTypeType    = AnnotationType
  type binaryAnnotationType  = JsonBinaryAnnotation
  type endpointType          = Endpoint
  type spanType              = Span
  type traceSummaryType      = TraceSummary

  def apply(a: annotationType): Annotation = a
  //def apply(a: Annotation): annotationType = a

  def apply(a: annotationTypeType): AnnotationType = a
  //def apply(a: AnnotationType): annotationTypeType = a

  def apply(b: binaryAnnotationType): BinaryAnnotation = {
    throw new Exception("Not implemented")
  }
  def apply(b: BinaryAnnotation): binaryAnnotationType = {
    val value = b.annotationType match {
      case AnnotationType(_, "bool")   => b.value.getInt
      case AnnotationType(_, "bytes")  => new String(b.value.array())
      case AnnotationType(_, "i16")    => b.value.getShort
      case AnnotationType(_, "i32")    => b.value.getInt
      case AnnotationType(_, "i64")    => b.value.getLong
      case AnnotationType(_, "double") => b.value.getDouble
      case AnnotationType(_, "string") => new String(b.value.array())
      case _ => throw new Exception("Uh oh")
    }
    JsonBinaryAnnotation(b.key, value, b.annotationType, b.host)
  }

  def apply(e: endpointType): Endpoint = e
  //def apply(e: Endpoint): endpointType = e

  def apply(s: spanType): Span = s
  //def apply(s: Span): spanType = s

  def apply(t: traceSummaryType): TraceSummary = t
  //def apply(t: TraceSummary): traceSummaryType = t
}

case class JsonBinaryAnnotation(key: String,
                                value: Any,
                                annotationType: AnnotationType,
                                host: Option[Endpoint])

