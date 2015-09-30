package com.twitter.zipkin.query

case class QueryRequest(serviceName: String,
                        spanName: Option[String],
                        annotations: List[String],
                        binaryAnnotations: Map[String, String],
                        endTs: Long,
                        limit: Int)