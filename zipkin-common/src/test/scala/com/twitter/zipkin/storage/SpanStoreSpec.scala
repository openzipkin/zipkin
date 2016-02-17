package com.twitter.zipkin.storage

import java.util.concurrent.TimeUnit._

import com.twitter.util.Await.result
import com.twitter.util.{Duration, Time}
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common._
import org.junit.{Before, Test}
import org.scalatest.Matchers
import org.scalatest.junit.JUnitSuite

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

  /** Notably, the cassandra implementation has day granularity */
  val day = MILLISECONDS.convert(1, DAYS)
  // Use real time, as most span-stores have TTL logic which looks back several days.
  val today = Time.now.floor(Duration.fromMilliseconds(day)).inMillis

  val ep = Endpoint(127 << 24 | 1, 8080, "service")

  val spanId = 456
  val ann1 = Annotation((today + 1) * 1000, Constants.ClientSend, Some(ep))
  val ann2 = Annotation((today + 2) * 1000, Constants.ServerRecv, None)
  val ann3 = Annotation((today + 10) * 1000, "custom", Some(ep))
  val ann4 = Annotation((today + 20) * 1000, "custom", Some(ep))
  val ann5 = Annotation((today + 5) * 1000, "custom", Some(ep))
  val ann6 = Annotation((today + 6) * 1000, "custom", Some(ep))
  val ann7 = Annotation((today + 7) * 1000, "custom", Some(ep))
  val ann8 = Annotation((today + 8) * 1000, "custom", Some(ep))

  val span1 = Span(123, "methodcall", spanId, None, Some(ann1.timestamp), Some(9000), List(ann1, ann3),
    List(BinaryAnnotation("BAH", "BEH", Some(ep))))
  val span2 = Span(456, "methodcall", spanId, None, Some(ann2.timestamp), None, List(ann2),
    List(BinaryAnnotation("BAH2", "BEH2", Some(ep))))
  val span3 = Span(789, "methodcall", spanId, None, Some(ann2.timestamp), Some(18000), List(ann2, ann3, ann4),
    List(BinaryAnnotation("BAH2", "BEH2", Some(ep))))
  val span4 = Span(999, "methodcall", spanId, None, Some(ann6.timestamp), Some(1000), List(ann6, ann7),
    List())
  val span5 = Span(999, "methodcall", spanId, None, Some(ann5.timestamp), Some(3000), List(ann5, ann8),
    List(BinaryAnnotation("BAH2", "BEH2", Some(ep))))

  val spanEmptySpanName = Span(123, "", spanId, None, Some(ann1.timestamp), Some(1000), List(ann1, ann2))
  val spanEmptyServiceName = Span(123, "spanname", spanId,
    binaryAnnotations = List(BinaryAnnotation("BAH2", "BEH2", Some(ep.copy(serviceName = "")))))

  val mergedSpan = Span(123, "methodcall", spanId, None, Some(ann1.timestamp), Some(1000),
    List(ann1, ann2), List(BinaryAnnotation("BAH2", "BEH2", Some(ep))))

  @Test def getSpansByTraceIds() {
    result(store(Seq(span1, span2)))
    result(store.getTracesByIds(Seq(span1.traceId))) should be(Seq(Seq(span1)))
    result(store.getTracesByIds(Seq(span1.traceId, span2.traceId, 111111))) should be(
      Seq(List(span2), List(span1))
    )
    // ids in wrong order
    result(store.getTracesByIds(Seq(span2.traceId, span1.traceId))) should be(
      Seq(List(span2), List(span1))
    )
  }

  /**
   * Filtered traces are returned in reverse insertion order. This is because the primary search
   * interface is a timeline view, looking back from an end timestamp.
   */
  @Test def tracesRetrieveInOrderDesc() {
    result(store(Seq(span2, span1.copy(annotations = List(ann3, ann1)))))

    result(store.getTracesByIds(Seq(span2.traceId, span1.traceId))) should be(
      Seq(List(span2), List(span1))
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

  @Test def getTraces_serviceNameInBinaryAnnotation() {
    val localTrace = List(Span(1L, "targz", 1L, None, Some(today * 1000 + 100L), Some(200L),
      binaryAnnotations = List(BinaryAnnotation(Constants.LocalComponent, "archiver", Some(ep)))))

    result(store(localTrace))

    result(store.getTraces(QueryRequest("service"))) should be(Seq(localTrace))
  }

  /** Shows that duration queries go against the root span, not the child */
  @Test def getTraces_duration() {
    val service1 = Endpoint(127 << 24 | 1, 8080, "service1")
    val service2 = Endpoint(127 << 24 | 2, 8080, "service2")
    val service3 = Endpoint(127 << 24 | 3, 8080, "service3")

    val archiver1 = List(BinaryAnnotation(Constants.LocalComponent, "archiver", Some(service1)))
    val archiver2 = List(BinaryAnnotation(Constants.LocalComponent, "archiver", Some(service2)))
    val archiver3 = List(BinaryAnnotation(Constants.LocalComponent, "archiver", Some(service3)))
    val targz = Span(1L, "targz", 1L, None, Some(today * 1000 + 100L), Some(200L), binaryAnnotations = archiver1)
    val tar = Span(1L, "tar", 2L, Some(1L), Some(today * 1000 + 200L), Some(150L), binaryAnnotations = archiver2)
    val gz = Span(1L, "gz", 3L, Some(1L), Some(today * 1000 + 250L), Some(50L), binaryAnnotations = archiver3)
    val zip = Span(3L, "zip", 3L, None, Some(today * 1000 + 130L), Some(50L), binaryAnnotations = archiver2)

    val trace1 = List(targz, tar, gz)
    val trace2 = List(
      targz.copy(traceId = 2L, timestamp = Some(today * 1000 + 110L), binaryAnnotations = archiver3),
      tar.copy(traceId = 2L, timestamp = Some(today * 1000 + 210L), binaryAnnotations = archiver2),
      gz.copy(traceId = 2L, timestamp = Some(today * 1000 + 260L), binaryAnnotations = archiver1))
    val trace3 = List(zip)

    result(store(trace1 ::: trace2 ::: trace3))

    val lookback = 12L * 60 * 60 * 1000 // 12hrs, instead of 7days
    val endTs = today + 1 // greater than all timestamps above
    val q = QueryRequest("placeholder", lookback = Some(lookback), endTs = endTs)

    // Min duration is inclusive and is applied by service.
    result(store.getTraces(q.copy(serviceName = "service1", minDuration = targz.duration))) should be(
      Seq(trace1)
    )
    result(store.getTraces(q.copy(serviceName = "service3", minDuration = targz.duration))) should be(
      Seq(trace2)
    )

    // Duration bounds aren't limited to root spans: they apply to all spans by service in a trace
    result(store.getTraces(q.copy(serviceName = "service2", minDuration = zip.duration, maxDuration = tar.duration))) should be(
      Seq(trace3, trace2, trace1) // service2 is in the middle of trace1 and 2, but root of trace3
    )

    // Span name should apply to the duration filter
    result(store.getTraces(q.copy(serviceName = "service2", spanName = Some("zip"), minDuration = zip.duration))) should be(
      Seq(trace3)
    )

    // Max duration should filter our longer spans from the same service
    result(store.getTraces(q.copy(serviceName = "service2", minDuration = gz.duration, maxDuration = zip.duration))) should be(
      Seq(trace3)
    )
  }

  /**
   * Spans and traces are meaningless unless they have a timestamp. While
   * unlikley, this could happen if a binary annotation is logged before a
   * timestamped one is.
   */
  @Test def getTraces_absentWhenNoTimestamp() {
    // store the binary annotations
    result(store(Seq(span1.copy(timestamp = None, duration = None, annotations = List.empty))))

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
    val foo = Span(1, "call1", 1, None, Some((today + 1) * 1000), None, List(Annotation((today + 1) * 1000, "foo", Some(ep))))
    // would be foo bar, except lexicographically bar precedes foo
    val barAndFoo = Span(2, "call2", 2, None, Some((today + 2) * 1000), None, List(Annotation((today + 2) * 1000, "bar", Some(ep)), Annotation((today + 2) * 1000, "foo", Some(ep))))
    val fooAndBazAndQux = Span(3, "call3", 3, None, Some((today + 3) * 1000), None, foo.annotations.map(_.copy(timestamp = (today + 3) * 1000)), List(BinaryAnnotation("baz", "qux", Some(ep))))
    val barAndFooAndBazAndQux = Span(4, "call4", 4, None, Some((today + 4) * 1000), None, barAndFoo.annotations.map(_.copy(timestamp = (today + 4) * 1000)), fooAndBazAndQux.binaryAnnotations)

    result(store(Seq(foo, barAndFoo, fooAndBazAndQux, barAndFooAndBazAndQux)))

    result(store.getTraces(QueryRequest("service", annotations = Set("foo")))) should be(
      Seq(List(barAndFooAndBazAndQux), List(fooAndBazAndQux), List(barAndFoo), List(foo))
    )

    result(store.getTraces(QueryRequest("service", annotations = Set("foo", "bar")))) should be(
      Seq(List(barAndFooAndBazAndQux), List(barAndFoo))
    )

    result(store.getTraces(QueryRequest("service", annotations = Set("foo", "bar"), binaryAnnotations = Set(("baz", "qux"))))) should be(
      Seq(List(barAndFooAndBazAndQux))
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

    result(store.getTraces(QueryRequest("service"))) should be(Seq(List(merged), List(span1)))
  }

  /** limit should apply to traces closest to endTs */
  @Test def getTraces_limit() {
    result(store(Seq(span1, span3))) // span1's timestamp is 1000, span3's timestamp is 2000

    result(store.getTraces(QueryRequest("service", limit = 1))) should be(Seq(List(span3)))
  }

  /** Traces whose root span has timestamps before or at endTs are returned */
  @Test def getTraces_endTsAndLookback() {
    result(store(Seq(span1, span3))) // span1's timestamp is 1000, span3's timestamp is 2000

    result(store.getTraces(QueryRequest("service", endTs = today + 1))) should be(Seq(List(span1)))
    result(store.getTraces(QueryRequest("service", endTs = today + 2))) should be(Seq(List(span3), List(span1)))
    result(store.getTraces(QueryRequest("service", endTs = today + 3))) should be(Seq(List(span3), List(span1)))
  }

  /** Traces whose root span has timestamps between (endTs - lookback) and endTs are returned */
  @Test def getTraces_lookback() {
    result(store(Seq(span1, span3))) // span1's timestamp is 1000, span3's timestamp is 2000

    result(store.getTraces(QueryRequest("service", endTs = today + 1, lookback = Some(1)))) should be(Seq(List(span1)))
    result(store.getTraces(QueryRequest("service", endTs = today + 2, lookback = Some(1)))) should be(Seq(List(span3), List(span1)))
    result(store.getTraces(QueryRequest("service", endTs = today + 3, lookback = Some(1)))) should be(Seq(List(span3)))
    result(store.getTraces(QueryRequest("service", endTs = today + 3, lookback = Some(2)))) should be(Seq(List(span3), List(span1)))
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
   * skew to correct is where a child appears to happen before the parent.
   *
   * It doesn't matter if clock-skew correction happens at storage or query
   * time, as long as it occurs by the time results are returned.
   *
   * Span stores who don't support this can override and disable this test,
   * noting in the README the limitation.
   */
  @Test def correctsClockSkew_whenSpanTimestampAndDurationAreDerivedFromAnnotations() {
    val client = Some(Endpoint(192 << 24 | 168 << 16 | 1, 8080, "client"))
    val frontend = Some(Endpoint(192 << 24 | 168 << 16 | 2, 8080, "frontend"))
    val backend = Some(Endpoint(192 << 24 | 168 << 16 | 3, 8080, "backend"))

    /** Intentionally not setting span.timestamp, duration */
    val parent = Span(1, "method1", 666, None, None, None, List(
      Annotation((today + 100) * 1000, Constants.ClientSend, client),
      Annotation((today + 95) * 1000, Constants.ServerRecv, frontend), // before client sends
      Annotation((today + 120) * 1000, Constants.ServerSend, frontend), // before client receives
      Annotation((today + 135) * 1000, Constants.ClientRecv, client)
    ).sorted)

    /** Intentionally not setting span.timestamp, duration */
    val remoteChild = Span(1, "remote", 777, Some(parent.id), None, None, List(
      Annotation((today + 100) * 1000, Constants.ClientSend, frontend),
      Annotation((today + 115) * 1000, Constants.ServerRecv, backend),
      Annotation((today + 120) * 1000, Constants.ServerSend, backend),
      Annotation((today + 115) * 1000, Constants.ClientRecv, frontend) // before server sent
    ))

    /** Local spans must explicitly set timestamp */
    val localChild = Span(1, "local", 778, Some(parent.id), Some((today + 101) * 1000), Some(50L),
      binaryAnnotations = List(BinaryAnnotation(Constants.LocalComponent, "framey", frontend))
    )

    val skewed = List(parent, remoteChild, localChild)

    // There's clock skew when the child doesn't happen after the parent
    skewed(0).annotations.head.timestamp should be <= skewed(1).annotations.head.timestamp
    skewed(0).annotations.head.timestamp should be <= skewed(2).timestamp.get // local span

    // Regardless of when clock skew is corrected, it should be corrected before traces return
    result(store(List(parent, remoteChild, localChild)))
    val adjusted = result(store.getTraces(QueryRequest("frontend")))(0)

    // After correction, children happen after their parent
    adjusted(0).timestamp.get should be <= adjusted(1).timestamp.get
    adjusted(0).timestamp.get should be <= adjusted(2).timestamp.get
    // .. because children are shifted to a later time
    adjusted(1).timestamp.get should be > skewed(1).annotations.head.timestamp
    adjusted(2).timestamp.get should be > skewed(2).timestamp.get // local span

    // And we do not change the parent (client) duration, due to skew in the child (server)
    adjusted(0).duration.get should be(clientDuration(skewed(0)))
    adjusted(1).duration.get should be(clientDuration(skewed(1)))
    adjusted(2).duration.get should be(skewed(2).duration.get)
  }

  // client duration is authoritative when present
  private[this] def clientDuration(span: Span) = {
    val clientAnnotations = span.annotations.filter(_.value.startsWith("c")).sorted
    clientAnnotations.last.timestamp - clientAnnotations.head.timestamp
  }
}
