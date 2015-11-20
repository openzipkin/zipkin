/**
 * Copyright 2015 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.zipkin;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;

import static io.zipkin.Constants.LOCAL_COMPONENT;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test for {@link SpanStore} implementations. Subtypes should create a connection to a real
 * backend, even if that backend is in-process.
 *
 * <p/>This is a replacement for {@code com.twitter.zipkin.storage.SpanStoreSpec}.
 */
public abstract class SpanStoreTest<T extends SpanStore> {

  /** Should maintain state between multiple calls within a test. */
  protected final T store;

  protected SpanStoreTest(T store) {
    this.store = store;
  }

  /** Clears the span store between tests. */
  @Before
  public abstract void clear();

  Endpoint ep = Endpoint.create("service", 127 << 24 | 1, 8080);

  long spanId = 456;
  Annotation ann1 = Annotation.create(1000L, "cs", ep);
  Annotation ann2 = Annotation.create(2000L, "sr", null);
  Annotation ann3 = Annotation.create(10000L, "custom", ep);
  Annotation ann4 = Annotation.create(20000L, "custom", ep);
  Annotation ann5 = Annotation.create(5000L, "custom", ep);
  Annotation ann6 = Annotation.create(6000L, "custom", ep);
  Annotation ann7 = Annotation.create(7000L, "custom", ep);
  Annotation ann8 = Annotation.create(8000L, "custom", ep);

  Span span1 = new Span.Builder()
      .traceId(123)
      .name("methodcall")
      .id(spanId)
      .timestamp(1000L).duration(9000L)
      .annotations(ann1, ann3)
      .binaryAnnotations(BinaryAnnotation.create("BAH", "BEH", ep)).build();

  Span span2 = new Span.Builder()
      .traceId(456)
      .name("methodcall")
      .id(spanId)
      .timestamp(2000L)
      .annotations(ann2)
      .binaryAnnotations(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span span3 = new Span.Builder()
      .traceId(789)
      .name("methodcall")
      .id(spanId)
      .timestamp(2000L).duration(18000L)
      .annotations(ann2, ann3, ann4)
      .binaryAnnotations(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span span4 = new Span.Builder()
      .traceId(999)
      .name("methodcall")
      .id(spanId)
      .timestamp(6000L).duration(1000L)
      .annotations(ann6, ann7).build();

  Span span5 = new Span.Builder()
      .traceId(999)
      .name("methodcall")
      .id(spanId)
      .timestamp(5000L).duration(3000L)
      .annotations(ann5, ann8)
      .binaryAnnotations(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span spanEmptySpanName = new Span.Builder()
      .traceId(123)
      .name("")
      .id(spanId)
      .parentId(1L)
      .timestamp(1000L).duration(1000L)
      .annotations(ann1, ann2).build();

  Span spanEmptyServiceName = new Span.Builder()
      .traceId(123)
      .name("spanname")
      .id(spanId).build();

  @Test
  public void getSpansByTraceIds() {
    store.accept(asList(span1, span2));
    assertThat(store.getTracesByIds(asList(span1.traceId))).containsExactly(asList(span1));
    assertThat(store.getTracesByIds(asList(span1.traceId, span2.traceId, 111111L)))
        .containsExactly(asList(span2), asList(span1));

    // ids in wrong order
    assertThat(store.getTracesByIds(asList(span2.traceId, span1.traceId)))
        .containsExactly(asList(span2), asList(span1));
  }

  /**
   * Filtered traces are returned in reverse insertion order. This is because the primary search
   * interface is a timeline view, looking back from an end timestamp.
   */
  @Test
  public void tracesRetrieveInOrderDesc() {
    store.accept(asList(span2, new Span.Builder(span1).annotations(ann3, ann1).build()));

    assertThat(store.getTracesByIds(asList(span2.traceId, span1.traceId)))
        .containsOnly(asList(span2), asList(span1));
  }

  /** Legacy instrumentation will not set timestamp and duration explicitly */
  @Test
  public void derivesTimestampAndDurationFromAnnotations() {
    store.accept(asList(new Span.Builder(span1).timestamp(null).duration(null).build()));

    assertThat(store.getTracesByIds(asList(span1.traceId)))
        .containsOnly(asList(span1));
  }

  @Test
  public void getSpansByTraceIds_empty() {
    assertThat(store.getTracesByIds(asList(54321L))).isEmpty();
  }

  @Test
  public void getSpanNames() {
    store.accept(asList(new Span.Builder(span1).name("yak").build(), span4));

    // should be in order
    assertThat(store.getSpanNames("service")).containsExactly("methodcall", "yak");
  }

  @Test
  public void getAllServiceNames() {
    BinaryAnnotation yak = BinaryAnnotation.address("sa", Endpoint.create("yak", 127 << 24 | 1, 8080));
    store.accept(asList(new Span.Builder(span1).binaryAnnotations(yak).build(), span4));

    // should be in order
    assertThat(store.getServiceNames()).containsExactly("service", "yak");
  }

  /**
   * This would only happen when the storage layer is bootstrapping, or has been purged.
   */
  @Test
  public void allShouldWorkWhenEmpty() {
    QueryRequest.Builder q = new QueryRequest.Builder("service");
    assertThat(store.getTraces(q.build())).isEmpty();
    assertThat(store.getTraces(q.spanName("methodcall").build())).isEmpty();
    assertThat(store.getTraces(q.addAnnotation("custom").build())).isEmpty();
    assertThat(store.getTraces(q.addBinaryAnnotation("BAH", "BEH").build())).isEmpty();
  }

  /**
   * This is unlikely and means instrumentation sends empty spans by mistake.
   */
  @Test
  public void allShouldWorkWhenNoAnnotationsYet() {
    store.accept(asList(spanEmptyServiceName));

    QueryRequest.Builder q = new QueryRequest.Builder("service");
    assertThat(store.getTraces(q.build())).isEmpty();
    assertThat(store.getTraces(q.spanName("methodcall").build())).isEmpty();
    assertThat(store.getTraces(q.addAnnotation("custom").build())).isEmpty();
    assertThat(store.getTraces(q.addBinaryAnnotation("BAH", "BEH").build())).isEmpty();
  }

  @Test
  public void getTraces_spanName() {
    store.accept(asList(span1));

    QueryRequest.Builder q = new QueryRequest.Builder("service");
    assertThat(store.getTraces(q.build()))
        .containsExactly(asList(span1));
    assertThat(store.getTraces(q.spanName("methodcall").build()))
        .containsExactly(asList(span1));

    assertThat(store.getTraces(q.spanName("badmethod").build())).isEmpty();
    assertThat(store.getTraces(q.serviceName("badservice").build())).isEmpty();
    assertThat(store.getTraces(q.spanName(null).build())).isEmpty();
  }

  @Test
  public void getTraces_serviceNameInBinaryAnnotation() {
    List<Span> localTrace = asList(new Span.Builder().traceId(1L).name("targz").id(1L)
        .timestamp(100L).duration(200L)
        .binaryAnnotations(BinaryAnnotation.create(LOCAL_COMPONENT, "archiver", ep)).build());

    store.accept(localTrace);

    assertThat(store.getTraces(new QueryRequest.Builder("service").build()))
        .containsExactly(localTrace);
  }

  /** Shows that duration queries go against the root span, not the child */
  @Test
  public void getTraces_duration() {
    Endpoint service1 = Endpoint.create("service1", 127 << 24 | 1, 8080);
    Endpoint service2 = Endpoint.create("service2", 127 << 24 | 2, 8080);
    Endpoint service3 = Endpoint.create("service3", 127 << 24 | 3, 8080);

    BinaryAnnotation.Builder component = new BinaryAnnotation.Builder().key(LOCAL_COMPONENT).value("archiver");
    BinaryAnnotation archiver1 = component.endpoint(service1).build();
    BinaryAnnotation archiver2 = component.endpoint(service2).build();
    BinaryAnnotation archiver3 = component.endpoint(service3).build();

    Span targz = new Span.Builder().traceId(1L).id(1L)
        .name("targz").timestamp(100L).duration(200L).binaryAnnotations(archiver1).build();
    Span tar = new Span.Builder().traceId(1L).id(2L).parentId(1L)
        .name("tar").timestamp(200L).duration(150L).binaryAnnotations(archiver2).build();
    Span gz = new Span.Builder().traceId(1L).id(3L).parentId(1L)
        .name("gz").timestamp(250L).duration(50L).binaryAnnotations(archiver3).build();
    Span zip = new Span.Builder().traceId(3L).id(3L)
        .name("zip").timestamp(130L).duration(50L).binaryAnnotations(archiver2).build();

    List<Span> trace1 = asList(targz, tar, gz);
    List<Span> trace2 = asList(
        new Span.Builder(targz).traceId(2L).timestamp(110L).binaryAnnotations(archiver3).build(),
        new Span.Builder(tar).traceId(2L).timestamp(210L).binaryAnnotations(archiver2).build(),
        new Span.Builder(gz).traceId(2L).timestamp(260L).binaryAnnotations(archiver1).build());
    List<Span> trace3 = asList(zip);

    store.accept(trace1);
    store.accept(trace2);
    store.accept(trace3);

    long lookback = 12L * 60 * 60 * 1000; // 12hrs, instead of 7days
    long endTs = 1000L; // greater than all timestamps above
    QueryRequest.Builder q = new QueryRequest.Builder("service1").lookback(lookback).endTs(endTs);

    // Min duration is inclusive and is applied by service.
    assertThat(store.getTraces(q.serviceName("service1").minDuration(targz.duration).build()))
        .containsOnly(trace1);

    assertThat(store.getTraces(q.serviceName("service3").minDuration(targz.duration).build()))
        .containsOnly(trace2);

    // Duration bounds aren't limited to root spans: they apply to all spans by service in a trace
    assertThat(store.getTraces(q.serviceName("service2").minDuration(zip.duration).maxDuration(tar.duration).build()))
        .containsOnly(trace3, trace2, trace1); // service2 is in the middle of trace1 and 2, but root of trace3

    // Span name should apply to the duration filter
    assertThat(store.getTraces(q.serviceName("service2").spanName("zip").maxDuration(zip.duration).build()))
        .containsOnly(trace3);

    // Max duration should filter our longer spans from the same service
    assertThat(store.getTraces(q.serviceName("service2").minDuration(gz.duration).maxDuration(zip.duration).build()))
        .containsOnly(trace3);
  }

  /**
   * Spans and traces are meaningless unless they have a timestamp. While unlikley, this could
   * happen if a binary annotation is logged before a timestamped one is.
   */
  @Test
  public void getTraces_absentWhenNoTimestamp() {
    // store the binary annotations
    store.accept(asList(new Span.Builder(span1).timestamp(null).duration(null).annotations().build()));

    assertThat(store.getTraces(new QueryRequest.Builder("service").build())).isEmpty();
    assertThat(store.getTraces(new QueryRequest.Builder("service").serviceName("methodcall").build())).isEmpty();

    // now store the timestamped annotations
    store.accept(asList(new Span.Builder(span1).binaryAnnotations().build()));

    assertThat(store.getTraces(new QueryRequest.Builder("service").build()))
        .containsExactly(asList(span1));
    assertThat(store.getTraces(new QueryRequest.Builder("service").spanName("methodcall").build()))
        .containsExactly(asList(span1));
  }

  @Test
  public void getTraces_annotation() {
    store.accept(asList(span1));

    // fetch by time based annotation, find trace
    assertThat(store.getTraces(new QueryRequest.Builder("service").addAnnotation("custom").build()))
        .containsExactly(asList(span1));

    // should find traces by the key and value annotation
    assertThat(store.getTraces(new QueryRequest.Builder("service").addBinaryAnnotation("BAH", "BEH").build()))
        .containsExactly(asList(span1));
  }

  @Test
  public void getTraces_multipleAnnotationsBecomeAndFilter() {
    Span foo = new Span.Builder().traceId(1).name("call1").id(1)
        .timestamp(1000L)
        .annotations(Annotation.create(1000, "foo", ep)).build();
    // would be foo bar, except lexicographically bar precedes foo
    Span barAndFoo = new Span.Builder().traceId(2).name("call2").id(2)
        .timestamp(2000L)
        .annotations(Annotation.create(2000, "bar", ep), Annotation.create(2000, "foo", ep)).build();
    Span fooAndBazAndQux = new Span.Builder().traceId(3).name("call3").id(3)
        .timestamp(3000L)
        .annotations(Annotation.create(3000, "foo", ep))
        .binaryAnnotations(BinaryAnnotation.create("baz", "qux", ep))
        .build();
    Span barAndFooAndBazAndQux = new Span.Builder().traceId(4).name("call4").id(4)
        .timestamp(4000L)
        .annotations(Annotation.create(4000, "bar", ep), Annotation.create(4000, "foo", ep))
        .binaryAnnotations(BinaryAnnotation.create("baz", "qux", ep))
        .build();

    store.accept(asList(foo, barAndFoo, fooAndBazAndQux, barAndFooAndBazAndQux));

    assertThat(store.getTraces(new QueryRequest.Builder("service").addAnnotation("foo").build()))
        .containsExactly(asList(barAndFooAndBazAndQux), asList(fooAndBazAndQux), asList(barAndFoo), asList(foo));

    assertThat(store.getTraces(new QueryRequest.Builder("service").addAnnotation("foo").addAnnotation("bar").build()))
        .containsExactly(asList(barAndFooAndBazAndQux), asList(barAndFoo));

    assertThat(store.getTraces(new QueryRequest.Builder("service").addAnnotation("foo").addAnnotation("bar").addBinaryAnnotation("baz", "qux").build()))
        .containsExactly(asList(barAndFooAndBazAndQux));
  }

  /**
   * It is expected that [[com.twitter.zipkin.storage.SpanStore.apply]] will receive the same span
   * id multiple times with different annotations. At query time, these must be merged.
   */
  @Test
  public void getTraces_mergesSpans() {
    List<Span> spans = asList(span1, span4, span5); // span4, span5 have the same span id
    store.accept(spans);

    SortedSet<Annotation> mergedAnnotations = new TreeSet<>(span4.annotations);
    mergedAnnotations.addAll(span5.annotations);

    Span merged = new Span.Builder(span4)
        .timestamp(mergedAnnotations.first().timestamp)
        .duration(mergedAnnotations.last().timestamp - mergedAnnotations.first().timestamp)
        .annotations(mergedAnnotations.toArray(new Annotation[0]))
        .binaryAnnotations(span5.binaryAnnotations.toArray(new BinaryAnnotation[0])).build();

    assertThat(store.getTraces(new QueryRequest.Builder("service").build()))
        .containsExactly(asList(merged), asList(span1));
  }

  /** limit should apply to traces closest to endTs */
  @Test
  public void getTraces_limit() {
    store.accept(asList(span1, span3)); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(store.getTraces(new QueryRequest.Builder("service").limit(1).build()))
        .containsExactly(asList(span3));
  }

  /** Traces whose root span has timestamps before or at endTs are returned */
  @Test
  public void getTraces_endTsAndLookback() {
    store.accept(asList(span1, span3)); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(store.getTraces(new QueryRequest.Builder("service").endTs(1L).build()))
        .containsExactly(asList(span1));
    assertThat(store.getTraces(new QueryRequest.Builder("service").endTs(2L).build()))
        .containsExactly(asList(span3), asList(span1));
    assertThat(store.getTraces(new QueryRequest.Builder("service").endTs(3L).build()))
        .containsExactly(asList(span3), asList(span1));
  }

  /** Traces whose root span has timestamps between (endTs - lookback) and endTs are returned */
  @Test
  public void getTraces_lookback() {
    store.accept(asList(span1, span3)); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(store.getTraces(new QueryRequest.Builder("service").endTs(1L).lookback(1L).build()))
        .containsExactly(asList(span1));
    assertThat(store.getTraces(new QueryRequest.Builder("service").endTs(2L).lookback(1L).build()))
        .containsExactly(asList(span3), asList(span1));
    assertThat(store.getTraces(new QueryRequest.Builder("service").endTs(3L).lookback(1L).build()))
        .containsExactly(asList(span3));
    assertThat(store.getTraces(new QueryRequest.Builder("service").endTs(3L).lookback(2L).build()))
        .containsExactly(asList(span3), asList(span1));
  }

  @Test
  public void getAllServiceNames_emptyServiceName() {
    store.accept(asList(spanEmptyServiceName));

    assertThat(store.getServiceNames()).isEmpty();
  }

  @Test
  public void getSpanNames_emptySpanName() {
    store.accept(asList(spanEmptySpanName));

    assertThat(store.getSpanNames(spanEmptySpanName.name)).isEmpty();
  }

  @Test
  public void spanNamesGoLowercase() {
    store.accept(asList(span1));

    assertThat(store.getTraces(new QueryRequest.Builder("service").spanName("MeThOdCaLl").build()))
        .containsOnly(asList(span1));
  }

  @Test
  public void serviceNamesGoLowercase() {
    store.accept(asList(span1));

    assertThat(store.getSpanNames("SeRvIcE")).containsExactly("methodcall");

    assertThat(store.getTraces(new QueryRequest.Builder("SeRvIcE").build()))
        .containsOnly(asList(span1));
  }

  /**
   * Basic clock skew correction is something span stores should support, until the UI supports
   * happens-before without using timestamps. The easiest clock skew to correct is where a child
   * appears to happen before the parent.
   *
   * <p/>It doesn't matter if clock-skew correction happens at storage or query time, as long as it
   * occurs by the time results are returned.
   *
   * <p/>Span stores who don't support this can override and disable this test, noting in the README
   * the limitation.
   */
  @Test
  public void correctsClockSkew() {
    Endpoint client = Endpoint.create("client", 192 << 24 | 168 << 16 | 1, 8080);
    Endpoint frontend = Endpoint.create("frontend", 192 << 24 | 168 << 16 | 2, 8080);
    Endpoint backend = Endpoint.create("backend", 192 << 24 | 168 << 16 | 3, 8080);

    Span parent = new Span.Builder()
        .traceId(1)
        .name("method1")
        .id(666)
        .timestamp(95000L).duration(40000L)
        .addAnnotation(Annotation.create(100000, Constants.CLIENT_SEND, client))
        .addAnnotation(Annotation.create(95000, Constants.SERVER_RECV, frontend)) // before client sends
        .addAnnotation(Annotation.create(120000, Constants.SERVER_SEND, frontend)) // before client receives
        .addAnnotation(Annotation.create(135000, Constants.CLIENT_RECV, client)).build();

    Span child = new Span.Builder()
        .traceId(1)
        .name("method2")
        .id(777)
        .parentId(666L)
        .timestamp(100000L).duration(20000L)
        .addAnnotation(Annotation.create(100000, Constants.CLIENT_SEND, frontend))
        .addAnnotation(Annotation.create(115000, Constants.SERVER_RECV, backend))
        .addAnnotation(Annotation.create(120000, Constants.SERVER_SEND, backend))
        .addAnnotation(Annotation.create(115000, Constants.CLIENT_RECV, frontend)) // before server sent
        .build();

    List<Span> skewed = asList(parent, child);

    // There's clock skew when the child doesn't happen after the parent
    assertThat(skewed.get(0).timestamp)
        .isLessThanOrEqualTo(skewed.get(1).timestamp);

    // Regardless of when clock skew is corrected, it should be corrected before traces return
    store.accept(asList(parent, child));
    List<Span> adjusted = store.getTracesByIds(asList(1L)).get(0);

    // After correction, the child happens after the parent
    assertThat(adjusted.get(0).timestamp)
        .isLessThanOrEqualTo(adjusted.get(1).timestamp);

    // .. because the child is shifted to a later date
    assertThat(adjusted.get(1).timestamp)
        .isGreaterThan(skewed.get(1).timestamp);

    // Since we've shifted the child to a later timestamp, the total duration appears shorter
    assertThat(adjusted.get(0).duration)
        .isLessThan(skewed.get(0).duration);

    // .. but that change in duration should be accounted for
    long shift = adjusted.get(0).timestamp - skewed.get(0).timestamp;
    assertThat(adjusted.get(0).duration)
        .isEqualTo(skewed.get(0).duration - shift);
  }
}
