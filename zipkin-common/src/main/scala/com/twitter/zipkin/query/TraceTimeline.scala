package com.twitter.zipkin.query

import com.twitter.zipkin.common.BinaryAnnotation

case class TraceTimeline(traceId: Long, rootSpanId: Long, annotations: Seq[TimelineAnnotation],
                         binaryAnnotations: Seq[BinaryAnnotation])
