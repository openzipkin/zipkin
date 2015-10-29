package com.twitter.finagle.zipkin.thrift

import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.finagle.tracing._
import com.twitter.util._
import com.twitter.zipkin.common.Annotation
import com.twitter.zipkin.storage.InMemorySpanStore
import com.twitter.zipkin.{Constants, common}
import org.junit.Test
import org.scalatest.junit.JUnitSuite

class SpanStoreZipkinTracerTest extends JUnitSuite {

  val finagleEndpoint = Endpoint(172 << 24 | 17 << 16 | 3, 8080)
  val finagleSpan = Span(
    traceId = TraceId(Some(SpanId(1)), None, SpanId(1), None, Flags().setDebug),
    annotations = Seq(
      ZipkinAnnotation(Time.fromMicroseconds(123), "cs", finagleEndpoint),
      ZipkinAnnotation(Time.fromMicroseconds(456), "cr", finagleEndpoint)
    ),
    _serviceName = Some("zipkin-query"),
    _name = Some("GET"),
    bAnnotations = Seq.empty[BinaryAnnotation],
    endpoint = finagleEndpoint)

  val endpoint = common.Endpoint(172 << 24 | 17 << 16 | 3, 8080, "zipkin-query")
  val span = common.Span(1L, "GET", 1L, None, List(
    Annotation(123, Constants.ClientSend, Some(endpoint)),
    Annotation(456, Constants.ClientRecv, Some(endpoint))),
    Seq.empty, Some(true)
  )

  @Test def sendsValidSpanAndIncrementsOk() {
    val stats = new InMemoryStatsReceiver()
    val spanStore = new InMemorySpanStore
    val tracer = new SpanStoreZipkinTracer(spanStore, stats)

    Await.result(tracer.logSpans(Seq(finagleSpan)))

    assert(spanStore.getTracesByIds(Seq(1L))() == Seq(Seq(span)))

    assert(stats.counters == Map(List("log_span", "ok") -> 1))
  }

  @Test def incrementsError() {
    val stats = new InMemoryStatsReceiver()
    val tracer = new SpanStoreZipkinTracer(new InMemorySpanStore(){
      override def apply(newSpans: Seq[common.Span]) = Future.exception(new NullPointerException).unit
    }, stats)

    Await.result(tracer.logSpans(Seq(finagleSpan)))

    assert(stats.counters == Map(List("log_span", "error", "java.lang.NullPointerException") -> 1))
  }
}
