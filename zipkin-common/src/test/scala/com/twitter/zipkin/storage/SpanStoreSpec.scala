package com.twitter.zipkin.storage

import com.google.common.net.InetAddresses._
import com.twitter.util.Await.{ready, result}
import com.twitter.zipkin.common.{Annotation, AnnotationType, BinaryAnnotation, Endpoint, Span}
import java.net.InetAddress._
import java.nio.ByteBuffer
import org.junit.{Before, Test}
import org.scalatest.junit.JUnitSuite
import org.scalatest.Matchers

/**
 * Base test for {@link SpanStore} implementations. Subtypes should create a
 * connection to a real backend, even if that backend is in-process.
 *
 * <p/> This is JUnit-based to allow overriding tests and use of annotations
 * such as {@link org.junit.Ignore} and {@link org.junit.ClassRule}.
 */
abstract class SpanStoreSpec extends JUnitSuite with Matchers {
  /**
   * Should maintain state between multiple calls within a test. Usually
   * implemented as a lazy.
   */
  def store: SpanStore

  /** Clears the span store between tests. */
  def clear

  @Before def before() = clear

  val ep = Endpoint(coerceToInteger(getByAddress(Array[Byte](127, 0, 0, 1))), 8080, "service")

  private def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), AnnotationType.String, Some(ep))

  val spanId = 456
  val ann1 = Annotation(1, "cs", Some(ep))
  val ann2 = Annotation(2, "sr", None)
  val ann3 = Annotation(20, "custom", Some(ep))
  val ann4 = Annotation(20, "custom", Some(ep))
  val ann5 = Annotation(5, "custom", Some(ep))
  val ann6 = Annotation(6, "custom", Some(ep))
  val ann7 = Annotation(7, "custom", Some(ep))
  val ann8 = Annotation(8, "custom", Some(ep))

  val span1 = Span(123, "methodcall", spanId, None, List(ann1, ann3),
    List(binaryAnnotation("BAH", "BEH")))
  val span2 = Span(456, "methodcall", spanId, None, List(ann2),
    List(binaryAnnotation("BAH2", "BEH2")))
  val span3 = Span(789, "methodcall", spanId, None, List(ann2, ann3, ann4),
    List(binaryAnnotation("BAH2", "BEH2")))
  val span4 = Span(999, "methodcall", spanId, None, List(ann6, ann7),
    List())
  val span5 = Span(999, "methodcall", spanId, None, List(ann5, ann8),
    List(binaryAnnotation("BAH2", "BEH2")))

  val spanEmptySpanName = Span(123, "", spanId, None, List(ann1, ann2), List())
  val spanEmptyServiceName = Span(123, "spanname", spanId, None, List(), List())

  val mergedSpan = Span(123, "methodcall", spanId, None,
    List(ann1, ann2), List(binaryAnnotation("BAH2", "BEH2")))

  @Test def getSpansByTraceIds() {
    ready(store(Seq(span1, span2)))
    result(store.getSpansByTraceIds(Seq(span1.traceId))) should be(Seq(Seq(span1)))
    result(store.getSpansByTraceIds(Seq(span1.traceId, span2.traceId, 111111))) should be(
      Seq(Seq(span1), Seq(span2))
    )
    // ids in wrong order
    result(store.getSpansByTraceIds(Seq(span2.traceId, span1.traceId))) should be(
      Seq(Seq(span1), Seq(span2))
    )
  }

  /** Spans can come out of order, and so can annotations within them */
  @Test def spansRetrieveInOrder() {
    ready(store(Seq(span2, span1.copy(annotations = List(ann3, ann1)))))

    result(store.getSpansByTraceIds(Seq(span2.traceId, span1.traceId))) should be(
      Seq(Seq(span1), Seq(span2))
    )
  }

  @Test def getSpansByTraceIds_empty() {
    result(store.getSpansByTraceIds(Seq(54321))) should be(empty)
  }

  @Test def getSpanNames() {
    ready(store(Seq(span1.copy(name = "yak"), span4)))

    // should be in order
    result(store.getSpanNames("service")) should be(List("methodcall", "yak"))
  }

  @Test def getAllServiceNames() {
    ready(store(Seq(span1.copy(annotations = List(ann1.copy(host = Some(ep.copy(serviceName = "yak"))))), span4)))

    // should be in order
    result(store.getAllServiceNames()) should be(List("service", "yak"))
  }

  @Test def getTraceIdsByName() {
    ready(store(Seq(span1)))

    result(store.getTraceIdsByName("service", None, 100, 3)).head.traceId should be(span1.traceId)
    result(store.getTraceIdsByName("service", Some("methodcall"), 100, 3)).head.traceId should be(span1.traceId)

    result(store.getTraceIdsByName("badservice", None, 100, 3)) should be(empty)
    result(store.getTraceIdsByName("service", Some("badmethod"), 100, 3)) should be(empty)
    result(store.getTraceIdsByName("badservice", Some("badmethod"), 100, 3)) should be(empty)
  }

  @Test def getTraceIdsByAnnotation() {
    ready(store(Seq(span1)))

    // fetch by time based annotation, find trace
    result(store.getTraceIdsByAnnotation("service", "custom", None, 100, 3)).map(_.traceId) should be(
      Seq(span1.traceId)
    )

    // should not find any traces since the core annotation doesn't exist in index
    result(store.getTraceIdsByAnnotation("service", "cs", None, 100, 3)) should be(empty)

    // should find traces by the key and value annotation
    result(store.getTraceIdsByAnnotation("service", "BAH", Some(ByteBuffer.wrap("BEH".getBytes)), 100, 3)).map(_.traceId) should be(
      Seq(span1.traceId)
    )
  }

  @Test def getTraceIdsByAnnotation_limit() {
    val spans = Seq(span1, span4, span5) // all have a "custom" annotation
    ready(store(spans))

    val res = result(store.getTraceIdsByAnnotation("service", "custom", None, 100, limit = 2))
    res.length should be(2)
    spans.map(_.traceId) should contain(res(0).traceId).and(contain(res(1).traceId))
  }

  @Test def getAllServiceNames_emptyServiceName() {
    ready(store(Seq(spanEmptyServiceName)))

    result(store.getAllServiceNames()) should be(empty)
  }

  @Test def getSpanNames_emptySpanName() {
    ready(store(Seq(spanEmptySpanName)))

    result(store.getSpanNames(spanEmptySpanName.name)) should be(empty)
  }
}
