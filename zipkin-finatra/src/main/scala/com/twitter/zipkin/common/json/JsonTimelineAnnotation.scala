package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.Endpoint

case class JsonTimelineAnnotation(timestamp: Long, value: String, host: Endpoint, spanId: String, parentId: Option[String],
                                  serviceName: String, spanName: String)