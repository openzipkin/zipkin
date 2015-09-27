package com.twitter.zipkin.json

import com.twitter.finagle.tracing.SpanId
import com.twitter.util.Bijection
import com.twitter.zipkin.common.Span

case class JsonSpan(traceId: String, // hex long
                    name: String,
                    id: String, // hex long
                    parentId: Option[String], // hex long
                    annotations: List[JsonAnnotation], // ordered by timestamp
                    binaryAnnotations: Seq[JsonBinaryAnnotation],
                    debug: Option[Boolean] = None)

object JsonSpanBijection extends Bijection[Span, JsonSpan] {
  override def apply(s: Span) = new JsonSpan(
    SpanId(s.traceId).toString(),
    s.name,
    SpanId(s.id).toString(),
    s.parentId.map(SpanId(_)).map(_.toString()),
    s.annotations.map(JsonAnnotationBijection),
    s.binaryAnnotations.map(JsonBinaryAnnotationBijection),
    if (s.debug) Some(true) else None
  )

  override def invert(s: JsonSpan) = Span.apply(
    SpanId.fromString(s.traceId).get.toLong,
    s.name,
    SpanId.fromString(s.id).get.toLong,
    s.parentId.flatMap(SpanId.fromString(_)).map(_.toLong),
    s.annotations.map(JsonAnnotationBijection.inverse),
    s.binaryAnnotations.map(JsonBinaryAnnotationBijection.inverse),
    s.debug.getOrElse(false)
  )
}