package com.twitter.zipkin.json

import com.google.common.io.BaseEncoding
import com.google.common.primitives.Longs
import com.twitter.zipkin.common.Span

case class JsonSpan(traceId: String, // hex long
                    name: String,
                    id: String, // hex long
                    parentId: Option[String] = None, // hex long
                    annotations: List[JsonAnnotation] = List.empty, // ordered by timestamp
                    binaryAnnotations: Seq[JsonBinaryAnnotation] = Seq.empty,
                    debug: Option[Boolean] = None)

object JsonSpan extends (Span => JsonSpan) {
  override def apply(s: Span) = new JsonSpan(
    id(s.traceId),
    s.name,
    id(s.id),
    s.parentId.map(id(_)),
    s.annotations.map(JsonAnnotation),
    s.binaryAnnotations.map(JsonBinaryAnnotation),
    s.debug
  )

  def invert(s: JsonSpan) = Span(
    id(s.traceId),
    s.name,
    id(s.id),
    s.parentId.map(id(_)),
    /** If deserialized with jackson, these could be null, as it doesn't look at default values. */
    if (s.annotations == null) List.empty else s.annotations.map(JsonAnnotation.invert).sorted,
    if (s.binaryAnnotations == null) Seq.empty else s.binaryAnnotations.map(JsonBinaryAnnotation.invert),
    s.debug
  )

  /** Strictly looks at length, so for a long, expects 16 ascii hex chars */
  private val hex = BaseEncoding.base16().lowerCase()

  private def id(l: Long) = hex.encode(Longs.toByteArray(l))

  private def id(idInHex: String) = {
    val array = if (idInHex.length < 16) {
      val correctLength = new Array[Char](16)
      java.util.Arrays.fill(correctLength, '0') // cause 0 in ASCII is NUL, not '0'
      System.arraycopy(idInHex.toCharArray, 0, correctLength, 16 - idInHex.length, idInHex.length)
      hex.decode(new String(correctLength))
    } else {
      hex.decode(idInHex)
    }
    Longs.fromByteArray(array)
  }
}