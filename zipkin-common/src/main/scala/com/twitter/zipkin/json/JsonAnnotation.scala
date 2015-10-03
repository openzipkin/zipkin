package com.twitter.zipkin.json

import com.twitter.zipkin.common.Annotation

case class JsonAnnotation(timestamp: Long, value: String, endpoint: Option[JsonEndpoint])

object JsonAnnotation extends (Annotation => JsonAnnotation) {
  override def apply(a: Annotation) =
    JsonAnnotation(a.timestamp, a.value, a.host.map(JsonEndpoint))

  def invert(a: JsonAnnotation) =
    Annotation(a.timestamp, a.value, a.endpoint.map(JsonEndpoint.invert))
}
