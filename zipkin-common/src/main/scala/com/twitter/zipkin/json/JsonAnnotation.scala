package com.twitter.zipkin.json

import com.twitter.util.Bijection
import com.twitter.zipkin.common.Annotation

case class JsonAnnotation(timestamp: Long, value: String, endpoint: Option[JsonEndpoint])

object JsonAnnotationBijection extends Bijection[Annotation, JsonAnnotation] {
  override def apply(a: Annotation) =
    JsonAnnotation(a.timestamp, a.value, a.host.map(JsonServiceBijection))

  override def invert(a: JsonAnnotation) =
    Annotation(a.timestamp, a.value, a.endpoint.map(JsonServiceBijection.inverse))
}
