package com.twitter.zipkin.storage.redis

import com.google.common.base.Charsets.UTF_8
import com.google.common.net.InetAddresses.coerceToInteger
import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.util.Await.result
import com.twitter.zipkin.common.{Annotation, AnnotationType, BinaryAnnotation, Endpoint, Span}
import java.net.InetAddress.getByAddress
import java.nio.ByteBuffer

class RedisStorageSpec extends RedisSpecification {

  var storage = new RedisStorage(_client, Some(7.days))
  val ep = Endpoint(coerceToInteger(getByAddress(Array[Byte](127, 0, 0, 1))), 8080, "service")

  val span = Span(123, "methodcall", 456, None,
    List(
      Annotation(1, "cs", Some(ep)),
      Annotation(2, "custom", Some(ep))
    ),
    List(
      BinaryAnnotation(
        "BAH",
        ByteBuffer.wrap("BEH".getBytes(UTF_8)),
        AnnotationType.String,
        Some(ep)
      )
    )
  )

  test("getTracesByIds empty") {
    result(storage.getSpansByTraceIds(List(span.traceId))) should be(Seq())
  }

  test("getSpansByTraceIds single") {
    result(storage.storeSpan(span))
    result(storage.getSpansByTraceIds(List(span.traceId))) should be(Seq(Seq(span)))
  }

  test("getSpansByTraceIds multiple") {
    val span2 = Span(456, "methodcall2", 789, None, span.annotations, span.binaryAnnotations)

    result(storage.storeSpan(span))
    result(storage.storeSpan(span2))
    result(storage.getSpansByTraceIds(List(span.traceId, span2.traceId))) should be(
      Seq(Seq(span), Seq(span2))
    )
  }
}
