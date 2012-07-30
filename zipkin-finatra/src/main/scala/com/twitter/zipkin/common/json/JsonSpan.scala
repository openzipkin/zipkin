package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.Annotation

case class JsonSpan(
  traceId: String,
  name: String,
  id: String,
  parentId: Option[String],
  services: Set[String],
  startTimestamp: Long,
  duration: Long,
  annotations: List[Annotation],
  binaryAnnotations: Seq[JsonBinaryAnnotation])
