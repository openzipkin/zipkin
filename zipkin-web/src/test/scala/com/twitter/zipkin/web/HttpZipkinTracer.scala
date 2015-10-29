package com.twitter.finagle.zipkin.thrift

import com.squareup.okhttp.mockwebserver.{MockResponse, MockWebServer}
import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.finagle.tracing._
import com.twitter.util._
import com.twitter.zipkin.common.Annotation
import com.twitter.zipkin.conversions.thrift.thriftListToSpans
import com.twitter.zipkin.{Constants, common}
import org.junit.{ClassRule, Test}
import org.scalatest.junit.JUnitSuite

object HttpZipkinTracerTest {
  // Singleton as the test needs to read the actual port in use
  val server = new MockWebServer()

  // Scala cannot generate fields with public visibility, so use a def instead.
  @ClassRule def serverDef = server
}

class HttpZipkinTracerTest extends JUnitSuite {
  import HttpZipkinTracerTest.server

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
    val tracer = new HttpZipkinTracer("localhost:" + server.getPort, stats)

    server.enqueue(new MockResponse)

    Await.result(tracer.logSpans(Seq(finagleSpan)))

    val request = server.takeRequest()
    assert(request.getMethod == "POST")
    assert(request.getPath == "/api/v1/spans")
    assert(request.getHeader("Content-Type") == "application/x-thrift")
    assert(thriftListToSpans(request.getBody.readByteArray()) == Seq(span))

    assert(stats.counters == Map(List("log_span", "ok") -> 1))
  }

  @Test def incrementsError() {
    val stats = new InMemoryStatsReceiver()
    val tracer = new HttpZipkinTracer("localhost:" + server.getPort, stats)

    server.enqueue(new MockResponse().setResponseCode(500))

    Await.result(tracer.logSpans(Seq(finagleSpan)))

    assert(stats.counters == Map(List("log_span", "ok") -> 1))
  }
}
