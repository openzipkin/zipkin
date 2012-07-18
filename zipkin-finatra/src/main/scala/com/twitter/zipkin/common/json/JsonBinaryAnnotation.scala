package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.{Endpoint, AnnotationType}

case class JsonBinaryAnnotation(key: String,
                                value: Any,
                                annotationType: AnnotationType,
                                host: Option[Endpoint])
