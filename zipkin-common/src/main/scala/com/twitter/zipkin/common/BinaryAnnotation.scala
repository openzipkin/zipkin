package com.twitter.zipkin.common

import java.nio.ByteBuffer

object BinaryAnnotation {

}

case class BinaryAnnotation(
  key: String,
  value: ByteBuffer,
  annotationType: AnnotationType,
  host: Option[Endpoint]
) {

}
