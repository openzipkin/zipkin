package com.twitter.zipkin.query

import com.twitter.zipkin.common.Endpoint

case class TimelineAnnotation(timestamp: Long, value: String, host: Endpoint, spanId: Long, parentId: Option[Long],
                              serviceName: String, spanName: String)
