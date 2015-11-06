package com.twitter.zipkin.storage

import com.google.common.net.InetAddresses._
import com.twitter.util.Await.result
import com.twitter.zipkin.Constants
import com.twitter.zipkin.adjuster.ApplyTimestampAndDuration
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
  val ann3 = Annotation(10, "custom", Some(ep))
  val ann4 = Annotation(20, "custom", Some(ep))
  val ann5 = Annotation(5, "custom", Some(ep))
  val ann6 = Annotation(6, "custom", Some(ep))
  val ann7 = Annotation(7, "custom", Some(ep))
  val ann8 = Annotation(8, "custom", Some(ep))

  val span1 = Span(123, "methodcall", spanId, None, Some(1), Some(9), List(ann1, ann3),
    List(binaryAnnotation("BAH", "BEH")))
  val span2 = Span(456, "methodcall", spanId, None, Some(2), None, List(ann2),
    List(binaryAnnotation("BAH2", "BEH2")))
  val span3 = Span(789, "methodcall", spanId, None, Some(2), Some(18), List(ann2, ann3, ann4),
    List(binaryAnnotation("BAH2", "BEH2")))
  val span4 = Span(999, "methodcall", spanId, None, Some(6), Some(1), List(ann6, ann7),
    List())
  val span5 = Span(999, "methodcall", spanId, None, Some(5), Some(3), List(ann5, ann8),
    List(binaryAnnotation("BAH2", "BEH2")))

  val spanEmptySpanName = Span(123, "", spanId, None, Some(1), Some(1), List(ann1, ann2))
  val spanEmptyServiceName = Span(123, "spanname", spanId)

  val mergedSpan = Span(123, "methodcall", spanId, None, Some(1), Some(1),
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

  /** Legacy instrumentation will not set timestamp and duration explicitly */
  @Test def derivesTimestampAndDurationFromAnnotations() {
    result(store(Seq(span1.copy(timestamp = None, duration = None))))

    result(store.getTracesByIds(Seq(span1.traceId))) should be(
      Seq(List(span1))
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

  /**
   * Spans and traces are meaningless unless they have a timestamp. While
   * unlikley, this could happen if a binary annotation is logged before a
   * timestamped one is.
   */
  @Test def getTraces_absentWhenNoTimestamp() {
    // store the binary annotations
    result(store(Seq(span1.copy(annotations = List.empty))))

    result(store.getTraces(QueryRequest("service"))) should be(empty)
    result(store.getTraces(QueryRequest("service", Some("methodcall")))) should be(empty)

    // now store the timestamped annotations
    result(store(Seq(span1.copy(binaryAnnotations = Seq.empty))))

    result(store.getTraces(QueryRequest("service"))) should be(
      Seq(Seq(span1))
    )
    result(store.getTraces(QueryRequest("service", Some("methodcall")))) should be(
      Seq(Seq(span1))
    )
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
    val foo = Span(1, "call1", 1, None, Some(1), None, List(Annotation(1, "foo", Some(ep))))
    // would be foo bar, except lexicographically bar precedes foo
    val barAndFoo = Span(2, "call2", 2, None, Some(2), None, List(Annotation(2, "bar", Some(ep)), Annotation(2, "foo", Some(ep))))
    val fooAndBazAndQux = Span(3, "call3", 3, None, Some(3), None, foo.annotations.map(_.copy(timestamp = 3)), List(binaryAnnotation("baz", "qux")))
    val barAndFooAndBazAndQux = Span(4, "call4", 4, None, Some(4), None, barAndFoo.annotations.map(_.copy(timestamp = 4)), fooAndBazAndQux.binaryAnnotations)

    result(store(Seq(foo, barAndFoo, fooAndBazAndQux, barAndFooAndBazAndQux)))
    result(store.getTraces(QueryRequest("service", annotations = Set("foo")))) should be(
      Seq(Seq(foo), Seq(barAndFoo), Seq(fooAndBazAndQux), Seq(barAndFooAndBazAndQux))
    )

    result(store.getTraces(QueryRequest("service", annotations = Set("foo", "bar")))) should be(
      Seq(Seq(barAndFoo), Seq(barAndFooAndBazAndQux))
    )

    result(store.getTraces(QueryRequest("service", annotations = Set("foo", "bar"), binaryAnnotations = Set(("baz", "qux"))))) should be(
      Seq(Seq(barAndFooAndBazAndQux))
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

    val mergedAnnotations = (span4.annotations ::: span5.annotations).sorted
    val merged = span4.copy(
      timestamp = Some(mergedAnnotations.head.timestamp),
      duration = Some(mergedAnnotations.last.timestamp - mergedAnnotations.head.timestamp),
      annotations = mergedAnnotations,
      binaryAnnotations = span5.binaryAnnotations)

    result(store.getTraces(QueryRequest("service"))) should be(Seq(List(span1), List(merged)))
  }

  @Test def getTraces_limit() {
    val spans = Seq(span1.copy(traceId = 1), span1.copy(traceId = 2), span1.copy(traceId = 3))
    result(store(spans))

    result(store.getTraces(QueryRequest("service", limit = 2))).size should be(2)
  }

  /** Traces whose root span has timestamps before or at endTs are returned */
  @Test def getTraces_endTs() {
    result(store(Seq(span1, span3))) // span1's timestamp is 1, span3's timestamp is 2

    result(store.getTraces(QueryRequest("service", endTs = 1))) should be(Seq(List(span1)))
    result(store.getTraces(QueryRequest("service", endTs = 2))) should be(Seq(List(span1), List(span3)))
    result(store.getTraces(QueryRequest("service", endTs = 3))) should be(Seq(List(span1), List(span3)))
  }

  @Test def getAllServiceNames_emptyServiceName() {
    result(store(Seq(spanEmptyServiceName)))

    result(store.getAllServiceNames()) should be(empty)
  }

  @Test def getSpanNames_emptySpanName() {
    result(store(Seq(spanEmptySpanName)))

    result(store.getSpanNames(spanEmptySpanName.name)) should be(empty)
  }

  @Test def spanNamesGoLowercase() {
    result(store(Seq(span1)))

    result(store.getTraces(QueryRequest("service", Some("MeThOdCaLl")))) should be(
      Seq(Seq(span1))
    )
  }

  @Test def serviceNamesGoLowercase() {
    result(store(Seq(span1)))

    result(store.getSpanNames("SeRvIcE")) should be(List("methodcall"))

    result(store.getTraces(QueryRequest("SeRvIcE"))) should be(
      Seq(Seq(span1))
    )
  }

  /**
   * Basic clock skew correction is something span stores should support, until
   * the UI supports happens-before without using timestamps. The easiest clock
   * skew to correct is where a child  appears to happen before the parent.
   *
   * It doesn't matter if clock-skew correction happens at storage or query
   * time, as long as it occurs by the time results are returned.
   *
   * Span stores who don't support this can override and disable this test,
   * noting in the README the limitation.
   */
  @Test def correctsClockSkew() {
    val client = Some(Endpoint(192 << 24 | 168 << 16 | 1, 8080, "client"))
    val frontend = Some(Endpoint(192 << 24 | 168 << 16 | 2, 8080, "frontend"))
    val backend = Some(Endpoint(192 << 24 | 168 << 16 | 3, 8080, "backend"))

    val parent = Span(1, "method1", 666, None, Some(95), Some(40), List(
      Annotation(100, Constants.ClientSend, client),
      Annotation(95, Constants.ServerRecv, frontend), // before client sends
      Annotation(120, Constants.ServerSend, frontend), // before client receives
      Annotation(135, Constants.ClientRecv, client)
    ).sorted)

    val child = Span(1, "method2", 777, Some(666L), Some(100), Some(20), List(
      Annotation(100, Constants.ClientSend, frontend),
      Annotation(115, Constants.ServerRecv, backend),
      Annotation(120, Constants.ServerSend, backend),
      Annotation(115, Constants.ClientRecv, frontend) // before server sent
    ))

    val skewed = List(parent, child)

    // There's clock skew when the child doesn't happen after the parent
    skewed(0).timestamp.get should be <= skewed(1).timestamp.get

    // Regardless of when clock skew is corrected, it should be corrected before traces return
    result(store(List(parent, child)))
    val adjusted = result(store.getTraces(QueryRequest("frontend")))(0)

    // After correction, the child happens after the parent
    adjusted(0).timestamp.get should be <= adjusted(1).timestamp.get
    // .. because the child is shifted to a later date
    adjusted(1).timestamp.get should be > skewed(1).timestamp.get

    // Since we've shifted the child to a later timestamp, the total duration appears shorter
    adjusted(0).duration.get should be < skewed(0).duration.get

    // .. but that change in duration should be accounted for
    val shift = adjusted(0).timestamp.get - skewed(0).timestamp.get
    adjusted(0).duration.get should be (skewed(0).duration.get - shift)
  }
}