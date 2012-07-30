package com.twitter.zipkin.common.json

case class JsonTraceTimeline(traceId: String, rootSpanId: String, annotations: Seq[JsonTimelineAnnotation],
                             binaryAnnotations: Seq[JsonBinaryAnnotation])
