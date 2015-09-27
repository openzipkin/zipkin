package com.twitter.zipkin.json

import com.google.common.io.BaseEncoding
import com.google.common.primitives.Longs
import com.twitter.zipkin.common.Span

import scala.util.control.NonFatal

case class JsonSpan(traceId: String, // hex long
                    name: String,
                    id: String, // hex long
                    parentId: Option[String], // hex long
                    annotations: List[JsonAnnotation], // ordered by timestamp
                    binaryAnnotations: Seq[JsonBinaryAnnotation],
                    debug: Option[Boolean] = None)

object JsonSpan extends (Span => JsonSpan) {
  override def apply(s: Span) = new JsonSpan(
    id(s.traceId),
    s.name,
    id(s.id),
    s.parentId.map(id(_)),
    s.annotations.map(JsonAnnotation),
    s.binaryAnnotations.map(JsonBinaryAnnotation),
    if (s.debug) Some(true) else None
  )

  def invert(s: JsonSpan) = Span.apply(
    id(s.traceId),
    s.name,
    id(s.id),
    s.parentId.map(id(_)),
    s.annotations.map(JsonAnnotation.invert),
    s.binaryAnnotations.map(JsonBinaryAnnotation.invert),
    s.debug.getOrElse(false)
  )

  private val hex = BaseEncoding.base16().lowerCase()

  private def id(l: Long) = hex.encode(Longs.toByteArray(l))

  private def id(idInHex: String) = try {
    val array = hex.decode(idInHex)
    Longs.fromByteArray(array)
  } catch {
    case NonFatal(e) => 0L
  }
}