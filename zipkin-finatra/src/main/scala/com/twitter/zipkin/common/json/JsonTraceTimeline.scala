package com.twitter.zipkin.common.json

import com.twitter.zipkin.query.TimelineAnnotation

case class JsonTraceTimeline(traceId: String, rootSpanId: String, annotations: Seq[TimelineAnnotation],
                             binaryAnnotations: Seq[JsonBinaryAnnotation])
