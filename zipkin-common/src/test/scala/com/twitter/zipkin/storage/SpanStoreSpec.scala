package com.twitter.zipkin.storage

import com.google.common.net.InetAddresses._
import com.twitter.util.Await.result
import com.twitter.zipkin.common._
import org.junit.{Before, Test}
import org.scalatest.Matchers
import org.scalatest.junit.JUnitSuite
import java.net.InetAddress._
import java.nio.ByteBuffer

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
    result(store(Seq(span1, span2)))
    result(store.getTracesByIds(Seq(span1.traceId))) should be(Seq(Seq(span1)))
    result(store.getTracesByIds(Seq(span1.traceId, span2.traceId, 111111))) should be(
      Seq(Seq(span1), Seq(span2))
    )
    // ids in wrong order
    result(store.getTracesByIds(Seq(span2.traceId, span1.traceId))) should be(
      Seq(Seq(span1), Seq(span2))
    )
  }

  /** Spans can come out of order, and so can annotations within them */
  @Test def spansRetrieveInOrder() {
    result(store(Seq(span2, span1.copy(annotations = List(ann3, ann1)))))

    result(store.getTracesByIds(Seq(span2.traceId, span1.traceId))) should be(
      Seq(Seq(span1), Seq(span2))
    )
  }

  @Test def getSpansByTraceIds_empty() {
    result(store.getTracesByIds(Seq(54321))) should be(empty)
  }

  @Test def getSpanNames() {
    result(store(Seq(span1.copy(name = "yak"), span4)))

    // should be in order
    result(store.getSpanNames("service")) should be(List("methodcall", "yak"))
  }

  @Test def getAllServiceNames() {
    result(store(Seq(span1.copy(annotations = List(ann1.copy(host = Some(ep.copy(serviceName = "yak"))))), span4)))

    // should be in order
    result(store.getAllServiceNames()) should be(List("service", "yak"))
  }

  /**
   * This would only happen when the storage layer is bootstrapping, or has been purged.
   */
  @Test def allShouldWorkWhenEmpty() {
    result(store.getTraces(QueryRequest("service"))) should be(empty)
    result(store.getTraces(QueryRequest("service", Some("methodcall")))) should be(empty)
    result(store.getTraces(QueryRequest("service", annotations = Set("custom")))) should be(empty)
    result(store.getTraces(QueryRequest("service", binaryAnnotations = Set(("BAH", "BEH"))))) should be(
      empty
    )
  }

  /**
   * This is unlikely and means instrumentation sends empty spans by mistake.
   */
  @Test def allShouldWorkWhenNoAnnotationsYet() {
    result(store(Seq(spanEmptyServiceName)))

    result(store.getTraces(QueryRequest("service"))) should be(empty)
    result(store.getTraces(QueryRequest("service", Some("methodcall")))) should be(empty)
    result(store.getTraces(QueryRequest("service", annotations = Set("custom")))) should be(empty)
    result(store.getTraces(QueryRequest("service", binaryAnnotations = Set(("BAH", "BEH"))))) should be(
      empty
    )
  }

  @Test def getTraces_spanName() {
    result(store(Seq(span1)))

    result(store.getTraces(QueryRequest("service"))) should be(
      Seq(Seq(span1))
    )
    result(store.getTraces(QueryRequest("service", Some("methodcall")))) should be(
      Seq(Seq(span1))
    )
    result(store.getTraces(QueryRequest("badservice"))) should be(empty)
    result(store.getTraces(QueryRequest("service", Some("badmethod")))) should be(empty)
    result(store.getTraces(QueryRequest("badservice", Some("badmethod")))) should be(empty)
  }

  @Test def getTraces_annotation() {
    result(store(Seq(span1)))

    // fetch by time based annotation, find trace
    result(store.getTraces(QueryRequest("service", annotations = Set("custom")))) should be(
      Seq(Seq(span1))
    )

    // should find traces by the key and value annotation
    result(store.getTraces(QueryRequest("service", binaryAnnotations = Set(("BAH", "BEH"))))) should be(
      Seq(Seq(span1))
    )
  }

  @Test def getTraces_multipleAnnotationsBecomeAndFilter() {
    val foo = Span(1, "call1", 1, None, List(Annotation(1, "foo", Some(ep))), List())
    val fooAndBar = Span(2, "call2", 2, None, List(Annotation(2, "foo", Some(ep)), Annotation(2, "bar", Some(ep))), List())
    val fooAndBazAndQux = Span(3, "call3", 3, None, foo.annotations.map(_.copy(timestamp = 3)), List(binaryAnnotation("baz", "qux")))
    val fooAndBarAndBazAndQux = Span(4, "call4", 4, None, fooAndBar.annotations.map(_.copy(timestamp = 4)), fooAndBazAndQux.binaryAnnotations)

    result(store(Seq(foo, fooAndBar, fooAndBazAndQux, fooAndBarAndBazAndQux)))

    result(store.getTraces(QueryRequest("service", annotations = Set("foo")))) should be(
      Seq(Seq(foo), Seq(fooAndBar), Seq(fooAndBazAndQux), Seq(fooAndBarAndBazAndQux))
    )

    result(store.getTraces(QueryRequest("service", annotations = Set("foo", "bar")))) should be(
      Seq(Seq(fooAndBar), Seq(fooAndBarAndBazAndQux))
    )

    result(store.getTraces(QueryRequest("service", annotations = Set("foo", "bar"), binaryAnnotations = Set(("baz", "qux"))))) should be(
      Seq(Seq(fooAndBarAndBazAndQux))
    )
  }

  /**
   * It is expected that [[com.twitter.zipkin.storage.SpanStore.apply]] will
   * receive the same span id multiple times with different annotations. At
   * query time, these must be merged.
   */
  @Test def getTraces_mergesSpans() {
    val spans = Seq(span1, span4, span5) // span4, span5 have the same span id
    result(store(spans))

    result(store.getTraces(QueryRequest("service"))) should be(
      Seq(Seq(span1), Seq(span4.copy(annotations = (span4.annotations ::: span5.annotations).sorted,
                                     binaryAnnotations = span5.binaryAnnotations)))
    )
  }

  @Test def getTraces_limit() {
    val spans = Seq(span1.copy(traceId = 1), span1.copy(traceId = 2), span1.copy(traceId = 3))
    result(store(spans))

    result(store.getTraces(QueryRequest("service", limit = 2))).size should be(2)
  }

  /** Traces who have span annotations before or at endTs are returned */
  @Test def getTraces_endTs() {
    val spans = Seq(span1) // created at timestamp 1; updated at timestamp 20
    result(store(spans))

    result(store.getTraces(QueryRequest("service", endTs = 19))) should be(empty)
    result(store.getTraces(QueryRequest("service", endTs = 20))) should be(Seq(Seq(span1)))
    result(store.getTraces(QueryRequest("service", endTs = 21))) should be(Seq(Seq(span1)))
  }

  @Test def getAllServiceNames_emptyServiceName() {
    result(store(Seq(spanEmptyServiceName)))

    result(store.getAllServiceNames()) should be(empty)
  }

  @Test def getSpanNames_emptySpanName() {
    result(store(Seq(spanEmptySpanName)))

    result(store.getSpanNames(spanEmptySpanName.name)) should be(empty)
  }
}
