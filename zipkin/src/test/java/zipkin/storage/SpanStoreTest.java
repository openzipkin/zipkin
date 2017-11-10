/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.CallbackCaptor;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.LOCAL_COMPONENT;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.TestObjects.APP_ENDPOINT;
import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.TODAY;
import static zipkin.TestObjects.WEB_ENDPOINT;

/**
 * Base test for {@link SpanStore} implementations. Subtypes should create a connection to a real
 * backend, even if that backend is in-process.
 *
 * <p>This is a replacement for {@code com.twitter.zipkin.storage.SpanStoreSpec}.
 */
public abstract class SpanStoreTest {

  /** Should maintain state between multiple calls within a test. */
  protected abstract StorageComponent storage();

  protected SpanStore store() {
    return storage().spanStore();
  }

  /** Blocks until the callback completes to allow read-your-writes consistency during tests. */
  protected void accept(Span... spans) {
    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    storage().asyncSpanConsumer().accept(asList(spans), captor);
    captor.get(); // block on result
  }

  /** Clears store between tests. */
  @Before
  public abstract void clear() throws IOException;

  Endpoint ep = Endpoint.create("service", 127 << 24 | 1);

  long spanId = 456;
  Annotation ann1 = Annotation.create((TODAY + 1) * 1000, "cs", ep);
  Annotation ann2 = Annotation.create((TODAY + 2) * 1000, "sr", ep);
  Annotation ann3 = Annotation.create((TODAY + 10) * 1000, "custom", ep);
  Annotation ann4 = Annotation.create((TODAY + 20) * 1000, "custom", ep);
  Annotation ann5 = Annotation.create((TODAY + 5) * 1000, "custom", ep);
  Annotation ann6 = Annotation.create((TODAY + 6) * 1000, "custom", ep);
  Annotation ann7 = Annotation.create((TODAY + 7) * 1000, "custom", ep);
  Annotation ann8 = Annotation.create((TODAY + 8) * 1000, "custom", ep);

  Span span1 = Span.builder()
      .traceId(123)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann1.timestamp)
      .annotations(asList(ann1, ann3))
      .addBinaryAnnotation(BinaryAnnotation.create("BAH", "BEH", ep)).build();

  Span span2 = Span.builder()
      .traceId(456)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann2.timestamp)
      .addAnnotation(ann2)
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span span3 = Span.builder()
      .traceId(789)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann2.timestamp).duration(18000L)
      .annotations(asList(ann2, ann3, ann4))
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span span4 = Span.builder()
      .traceId(999)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann6.timestamp).duration(1000L)
      .annotations(asList(ann6, ann7)).build();

  Span span5 = Span.builder()
      .traceId(999)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann5.timestamp).duration(3000L)
      .annotations(asList(ann5, ann8))
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span spanEmptySpanName = Span.builder()
      .traceId(123)
      .name("")
      .id(spanId)
      .parentId(1L)
      .timestamp(ann1.timestamp).duration(1000L)
      .annotations(asList(ann1, ann2)).build();

  Span spanEmptyServiceName = Span.builder()
      .traceId(123)
      .name("spanname")
      .id(spanId).build();

  @Test
  public void getTrace_noTraceIdHighDefaultsToZero() {
    span1 = TestObjects.TRACE.get(0).toBuilder().traceIdHigh(0L).build();
    span2 = span1.toBuilder().traceId(1111L).build();

    accept(span1, span2);
    assertThat(store().getTrace(span1.traceId)).isEqualTo(asList(span1));
    assertThat(store().getTrace(0L, span1.traceId)).isEqualTo(asList(span1));
  }

  @Test
  public void getTrace_128() {
    span1 = span1.toBuilder().traceIdHigh(1L).build();
    span2 = span1.toBuilder().traceIdHigh(2L).build();

    accept(span1, span2);

    for (Span span: asList(span1, span2)) {
      assertThat(store().getTrace(span.traceIdHigh, span.traceId))
          .isNotNull()
          .extracting(t -> t.traceIdHigh)
          .containsExactly(span.traceIdHigh);
    }
  }

  @Test
  public void getTraces_128() {
    Span span1 = TestObjects.TRACE.get(0).toBuilder().traceIdHigh(1L)
        .binaryAnnotations(asList(BinaryAnnotation.create("key", "value1", WEB_ENDPOINT))).build();
    Span span2 = span1.toBuilder().traceIdHigh(2L)
        .binaryAnnotations(asList(BinaryAnnotation.create("key", "value2", WEB_ENDPOINT))).build();

    accept(span1, span2);

    assertThat(
        store().getTraces(QueryRequest.builder().serviceName(WEB_ENDPOINT.serviceName)
            .addBinaryAnnotation("key", "value2")
            .build()))
        .containsExactly(asList(span2));
  }

  @Test
  public void getTrace_nullWhenNotFound() {
    assertThat(store().getTrace(0L, 111111L)).isNull();
    assertThat(store().getTrace(222222L, 111111L)).isNull();
    assertThat(store().getRawTrace(0L, 111111L)).isNull();
    assertThat(store().getRawTrace(222222L, 111111L)).isNull();
  }

  /**
   * Filtered traces are returned in reverse insertion order. This is because the primary search
   * interface is a timeline view, looking back from an end timestamp.
   */
  @Test
  public void tracesRetrieveInOrderDesc() {
    accept(span2, span1.toBuilder().annotations(asList(ann3, ann1)).build());

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span2.id, span1.id);
  }

  /** Legacy instrumentation will not set timestamp and duration explicitly */
  @Test
  public void derivesTimestampAndDurationFromAnnotations() {
    accept(span1.toBuilder().timestamp(null).duration(null).build());

    assertThat(store().getTrace(span1.traceIdHigh, span1.traceId))
        .containsOnly(ApplyTimestampAndDuration.apply(span1));
  }

  @Test
  public void getSpanNames() {
    accept(span1.toBuilder().name("yak").build(), span4);

    // should be in order
    assertThat(store().getSpanNames("service")).containsExactly("methodcall", "yak");
  }

  @Test
  public void getSpanNames_allReturned() {
    // Assure a default spanstore limit isn't hit by assuming if 50 are returned, all are returned
    List<String> spanNames = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      String suffix = i < 10 ? "0" + i : String.valueOf(i);
      accept(span1.toBuilder().id(i).name("yak" + suffix).build());
      spanNames.add("yak" + suffix);
    }

    // should be in order
    assertThat(store().getSpanNames("service")).containsOnlyElementsOf(spanNames);
  }

  @Test
  public void getAllServiceNames_mergesAnnotation_andBinaryAnnotation() {
    // creates a span with mutual exclusive endpoints in binary annotations and annotations
    BinaryAnnotation yak = BinaryAnnotation.address("sa", Endpoint.create("yak", 127 << 24 | 1));
    accept(span1.toBuilder().binaryAnnotations(asList(yak)).build());

    // should be in order
    assertThat(store().getServiceNames()).containsExactly("service", "yak");
  }

  @Test
  public void getAllServiceNames__allReturned() {
    // Assure a default spanstore limit isn't hit by assuming if 50 are returned, all are returned
    List<String> serviceNames = new ArrayList<>();
    serviceNames.add("service");
    for (int i = 0; i < 50; i++) {
      String suffix = i < 10 ? "0" + i : String.valueOf(i);
      BinaryAnnotation yak =
          BinaryAnnotation.address("sa", Endpoint.create("yak" + suffix, 127 << 24 | 1));
      accept(span1.toBuilder().id(i).addBinaryAnnotation(yak).build());
      serviceNames.add("yak" + suffix);
    }

    assertThat(store().getServiceNames()).containsOnlyElementsOf(serviceNames);
  }

  /**
   * This would only happen when the store layer is bootstrapping, or has been purged.
   */
  @Test
  public void allShouldWorkWhenEmpty() {
    QueryRequest.Builder q = QueryRequest.builder().serviceName("service");
    assertThat(store().getTraces(q.build())).isEmpty();
    assertThat(store().getTraces(q.spanName("methodcall").build())).isEmpty();
    assertThat(store().getTraces(q.addAnnotation("custom").build())).isEmpty();
    assertThat(store().getTraces(q.addBinaryAnnotation("BAH", "BEH").build())).isEmpty();
  }

  /**
   * This is unlikely and means instrumentation sends empty spans by mistake.
   */
  @Test
  public void allShouldWorkWhenNoAnnotationsYet() {
    accept(spanEmptyServiceName);

    QueryRequest.Builder q = QueryRequest.builder().serviceName("service");
    assertThat(store().getTraces(q.build())).isEmpty();
    assertThat(store().getTraces(q.spanName("methodcall").build())).isEmpty();
    assertThat(store().getTraces(q.addAnnotation("custom").build())).isEmpty();
    assertThat(store().getTraces(q.addBinaryAnnotation("BAH", "BEH").build())).isEmpty();
  }

  @Test
  public void getTraces_spanName() {
    accept(span1);

    QueryRequest.Builder q = QueryRequest.builder().serviceName("service");
    assertThat(store().getTraces(q.build()))
        .hasSize(1);
    assertThat(store().getTraces(q.spanName("methodcall").build()))
        .hasSize(1);

    assertThat(store().getTraces(q.spanName("badmethod").build())).isEmpty();
    assertThat(store().getTraces(q.serviceName("badservice").build())).isEmpty();
    assertThat(store().getTraces(q.spanName(null).build())).isEmpty();
  }

  @Test
  public void getTraces_spanName_128() {
    span1 = span1.toBuilder().traceIdHigh(1L).name("foo").build();
    span2 = span1.toBuilder().traceIdHigh(2L).name("bar").build();
    accept(span1, span2);

    QueryRequest.Builder q = QueryRequest.builder().serviceName("service");
    assertThat(store().getTraces(q.spanName(span1.name).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span1.id);
  }

  @Test
  public void getTraces_serviceNameInBinaryAnnotation() {
    Span localTrace = Span.builder().traceId(1L).name("targz").id(1L)
        .timestamp(TODAY * 1000 + 100L).duration(200L)
        .addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "archiver", ep)).build();

    accept(localTrace);

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").build()))
        .containsExactly(asList(localTrace));
  }

  /**
   * While large spans are discouraged, and maybe not indexed, we should be able to read them back.
   */
  @Test
  public void readsBackLargeValues() {
    char[] kilobyteOfText = new char[1024];
    Arrays.fill(kilobyteOfText, 'a');

    // Make a span that's over 1KiB in size
    Span span = Span.builder().traceId(1L).name("big").id(1L)
        .timestamp(TODAY * 1000 + 100L).duration(200L)
        .addBinaryAnnotation(BinaryAnnotation.create("a", new String(kilobyteOfText), ep)).build();

    accept(span);

    // read back to ensure the data wasn't truncated
    assertThat(store().getTraces(QueryRequest.builder().build()))
        .containsExactly(asList(span));
    assertThat(store().getTrace(span.traceIdHigh, span.traceId))
        .isEqualTo(asList(span));
  }

  /**
   * Formerly, a bug was present where cassandra didn't index more than bucket count traces per
   * millisecond. This stores a lot of spans to ensure indexes work under high-traffic scenarios.
   */
  @Test
  public void getTraces_manyTraces() {
    int traceCount = 1000;
    Span span = TestObjects.LOTS_OF_SPANS[0];
    BinaryAnnotation b = span.binaryAnnotations.get(0);

    accept(Arrays.copyOfRange(TestObjects.LOTS_OF_SPANS, 0, traceCount));

    assertThat(store().getTraces(new QueryRequest.Builder().limit(traceCount).build()))
        .hasSize(traceCount);

    QueryRequest.Builder builder =
        QueryRequest.builder().limit(traceCount).serviceName(b.endpoint.serviceName);

    assertThat(store().getTraces(builder.build()))
        .hasSize(traceCount);

    assertThat(store().getTraces(builder.spanName(span.name).build()))
        .hasSize(traceCount);

    assertThat(store().getTraces(builder.addBinaryAnnotation(b.key, new String(b.value)).build()))
        .hasSize(traceCount);
  }

  /** Shows that duration queries go against the root span, not the child */
  @Test
  public void getTraces_duration() {
    setupDurationData();

    QueryRequest.Builder q = QueryRequest.builder().lookback(DAY); // instead of since epoch
    QueryRequest query;

    // Min duration is inclusive and is applied by service.
    query = q.serviceName("service1").minDuration(200_000L).build();
    assertThat(store().getTraces(query)).extracting(t -> t.get(0).traceId)
      .containsExactly(1L);

    query = q.serviceName("service3").minDuration(200_000L).build();
    assertThat(store().getTraces(query)).extracting(t -> t.get(0).traceId)
      .containsExactly(2L);

    // Duration bounds aren't limited to root spans: they apply to all spans by service in a trace
    query = q.serviceName("service2").minDuration(50_000L).maxDuration(150_000L).build();
    assertThat(store().getTraces(query)).extracting(t -> t.get(0).traceId)
      .containsExactly(3L, 2L, 1L); // service2 root of trace 3, but middle of 1 and 2.

    // Span name should apply to the duration filter
    query = q.serviceName("service2").spanName("zip").maxDuration(50_000L).build();
    assertThat(store().getTraces(query)).extracting(t -> t.get(0).traceId)
      .containsExactly(3L);

    // Max duration should filter our longer spans from the same service
    query = q.serviceName("service2").minDuration(50_000L).maxDuration(50_000L).build();
    assertThat(store().getTraces(query)).extracting(t -> t.get(0).traceId)
      .containsExactly(3L);
  }

  @Test
  public void getTraces_exactMatch() {
    exactMatch(ep.serviceName);
  }

  @Test
  public void getTraces_exactMatch_allServices() {
    exactMatch(null);
  }

  void exactMatch(String serviceName) {
    Span span = Span.builder()
      .traceId(123)
      .name("method")
      .id(123)
      .timestamp(TODAY * 1000)
      .addAnnotation(Annotation.create(TODAY * 1000, "retry", ep))
      .addBinaryAnnotation(BinaryAnnotation.create("http.path", "/a", ep)).build();
    accept(span);

    QueryRequest query;

    // Exact match
    query = QueryRequest.builder().lookback(DAY).serviceName(serviceName)
      .spanName("method")
      .addAnnotation("retry")
      .addBinaryAnnotation("http.path", "/a")
      .build();
    assertThat(store().getTraces(query)).hasSize(1);

    // substring spanName
    query = QueryRequest.builder().lookback(DAY).serviceName(serviceName).spanName("thod").build();
    assertThat(store().getTraces(query)).isEmpty();
    query = QueryRequest.builder().lookback(DAY).serviceName(serviceName).spanName("meth").build();
    assertThat(store().getTraces(query)).isEmpty();

    // substring annotation
    query =
      QueryRequest.builder().lookback(DAY).serviceName(serviceName).addAnnotation("retr").build();
    assertThat(store().getTraces(query)).isEmpty();
    query =
      QueryRequest.builder().lookback(DAY).serviceName(serviceName).addAnnotation("etry").build();
    assertThat(store().getTraces(query)).isEmpty();

    // substring tag
    query = QueryRequest.builder()
      .lookback(DAY)
      .serviceName(serviceName)
      .addBinaryAnnotation("http", "/a")
      .build();
    assertThat(store().getTraces(query)).isEmpty();
    query = QueryRequest.builder()
      .lookback(DAY)
      .serviceName(serviceName)
      .addBinaryAnnotation("path", "/a")
      .build();
    assertThat(store().getTraces(query)).isEmpty();
    query = QueryRequest.builder()
      .lookback(DAY)
      .serviceName(serviceName)
      .addBinaryAnnotation("http.path", "a")
      .build();
    assertThat(store().getTraces(query)).isEmpty();
    query = QueryRequest.builder()
      .lookback(DAY)
      .serviceName(serviceName)
      .addBinaryAnnotation("http.path", "/")
      .build();
    assertThat(store().getTraces(query)).isEmpty();
  }

  @Test
  public void getTraces_duration_allServices() {
    setupDurationData();

    QueryRequest query;

    // Annotation value name should apply to the duration filter
    query = QueryRequest.builder().lookback(DAY) // reset to query across all services
      .addAnnotation("zip").minDuration(50_000L).build();
    assertThat(store().getTraces(query)).extracting(t -> t.get(0).traceId)
      .containsExactly(3L);

    // Binary annotation value should apply to the duration filter
    query = QueryRequest.builder().lookback(DAY) // reset to query across all services
      .addBinaryAnnotation(LOCAL_COMPONENT, "archiver-v2").minDuration(50_000L).build();
    assertThat(store().getTraces(query)).extracting(t -> t.get(0).traceId)
      .containsExactly(2L);
  }

  void setupDurationData() {
    Endpoint service1 = Endpoint.create("service1", 127 << 24 | 1);
    Endpoint service2 = Endpoint.create("service2", 127 << 24 | 2);
    Endpoint service3 = Endpoint.create("service3", 127 << 24 | 3);

    BinaryAnnotation.Builder component = BinaryAnnotation.builder().key(LOCAL_COMPONENT);
    BinaryAnnotation archiver1 = component.value("archiver").endpoint(service1).build();
    BinaryAnnotation archiver2 = component.value("archiver").endpoint(service2).build();
    BinaryAnnotation archiver3 = component.value("archiver").endpoint(service3).build();

    long offsetMicros = (TODAY - 3) * 1000L; // to make sure queries look back properly
    Span targz = Span.builder().traceId(1L).id(1L)
      .name("targz").timestamp(offsetMicros + 100L).duration(200_000L)
      .addBinaryAnnotation(archiver1).build();
    Span tar = Span.builder().traceId(1L).id(2L).parentId(1L)
      .name("tar").timestamp(offsetMicros + 200L).duration(150_000L)
      .addBinaryAnnotation(archiver2).build();
    Span gz = Span.builder().traceId(1L).id(3L).parentId(1L)
      .name("gz").timestamp(offsetMicros + 250L).duration(50_000L)
      .addBinaryAnnotation(archiver3).build();
    Span zip = Span.builder().traceId(3L).id(3L)
      .name("zip").timestamp(offsetMicros + 130L).duration(50_000L)
      .addAnnotation(Annotation.builder().timestamp(offsetMicros + 130L).value("zip").build())
      .addBinaryAnnotation(archiver2).build();

    List<Span> trace1 = asList(targz, tar, gz);
    List<Span> trace2 = asList(
      targz.toBuilder().traceId(2L).timestamp(offsetMicros + 110L)
        .binaryAnnotations(asList(
          component.value("archiver-v2").endpoint(service3).build()
        )).build(),
      tar.toBuilder().traceId(2L).timestamp(offsetMicros + 210L)
        .binaryAnnotations(asList(archiver2)).build(),
      gz.toBuilder().traceId(2L).timestamp(offsetMicros + 260L)
        .binaryAnnotations(asList(archiver1)).build());
    List<Span> trace3 = asList(zip);

    accept(trace1.toArray(new Span[0]));
    accept(trace2.toArray(new Span[0]));
    accept(trace3.toArray(new Span[0]));
  }

  /**
   * Spans and traces are meaningless unless they have a timestamp. While unlikley, this could
   * happen if a binary annotation is logged before a timestamped one is.
   */
  @Test
  public void getTraces_absentWhenNoTimestamp() {
    // store the binary annotations
    accept(span1.toBuilder().timestamp(null).duration(null).annotations(emptyList()).build());

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").build())).isEmpty();
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").serviceName("methodcall").build())).isEmpty();

    // now store the timestamped annotations
    accept(span1.toBuilder().binaryAnnotations(emptyList()).build());

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").build()))
        .hasSize(1);
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").spanName("methodcall").build()))
        .hasSize(1);
  }

  @Test
  public void getTraces_annotation() {
    accept(span1);

    // fetch by time based annotation, find trace
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("custom").build()))
        .hasSize(1);

    // should find traces by the key and value annotation
    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").addBinaryAnnotation("BAH", "BEH").build()))
        .hasSize(1);
  }

  @Test
  public void getTraces_multipleAnnotationsBecomeAndFilter() {
    Span foo = Span.builder().traceId(1).name("call1").id(1)
        .timestamp((TODAY + 1) * 1000)
        .addAnnotation(Annotation.create((TODAY + 1) * 1000, "foo", ep)).build();
    // would be foo bar, except lexicographically bar precedes foo
    Span barAndFoo = Span.builder().traceId(2).name("call2").id(2)
        .timestamp((TODAY + 2) * 1000)
        .addAnnotation(Annotation.create((TODAY + 2) * 1000, "bar", ep))
        .addAnnotation(Annotation.create((TODAY + 2) * 1000, "foo", ep)).build();
    Span fooAndBazAndQux = Span.builder().traceId(3).name("call3").id(3)
        .timestamp((TODAY + 3) * 1000)
        .addAnnotation(Annotation.create((TODAY + 3) * 1000, "foo", ep))
        .addBinaryAnnotation(BinaryAnnotation.create("baz", "qux", ep))
        .build();
    Span barAndFooAndBazAndQux = Span.builder().traceId(4).name("call4").id(4)
        .timestamp((TODAY + 4) * 1000)
        .addAnnotation(Annotation.create((TODAY + 4) * 1000, "bar", ep))
        .addAnnotation(Annotation.create((TODAY + 4) * 1000, "foo", ep))
        .addBinaryAnnotation(BinaryAnnotation.create("baz", "qux", ep))
        .build();

    accept(foo, barAndFoo, fooAndBazAndQux, barAndFooAndBazAndQux);

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("foo").build()))
        .containsExactly(asList(barAndFooAndBazAndQux), asList(fooAndBazAndQux), asList(barAndFoo), asList(foo));

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("foo").addAnnotation("bar").build()))
        .containsExactly(asList(barAndFooAndBazAndQux), asList(barAndFoo));

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("foo").addAnnotation("bar").addBinaryAnnotation("baz", "qux").build()))
        .containsExactly(asList(barAndFooAndBazAndQux));

    // ensure we can search only by tag/binaryAnnotation key
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("baz").build()))
        .containsExactly(asList(barAndFooAndBazAndQux), asList(fooAndBazAndQux));
  }

  /**
   * This test makes sure that annotation queries pay attention to which host logged an annotation.
   */
  @Test
  public void getTraces_differentiateOnServiceName() {
    Span trace1 = Span.builder().traceId(1).name("get").id(1)
        .timestamp((TODAY + 1) * 1000)
        .duration(3000L)
        .addAnnotation(Annotation.create((TODAY + 1) * 1000, CLIENT_SEND, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(((TODAY + 1) * 1000) + 500, "web", WEB_ENDPOINT))
        .addAnnotation(Annotation.create((TODAY + 2) * 1000, SERVER_RECV, APP_ENDPOINT))
        .addAnnotation(Annotation.create((TODAY + 3) * 1000, SERVER_SEND, APP_ENDPOINT))
        .addAnnotation(Annotation.create((TODAY + 4) * 1000, CLIENT_RECV, WEB_ENDPOINT))
        .addBinaryAnnotation(BinaryAnnotation.create("local", "web", WEB_ENDPOINT))
        .addBinaryAnnotation(BinaryAnnotation.create("web-b", "web", WEB_ENDPOINT))
        .build();

    Span trace2 = Span.builder().traceId(2).name("get").id(2)
        .timestamp((TODAY + 11) * 1000)
        .duration(3000L)
        .addAnnotation(Annotation.create((TODAY + 11) * 1000, CLIENT_SEND, APP_ENDPOINT))
        .addAnnotation(Annotation.create(((TODAY + 11) * 1000) + 500, "app", APP_ENDPOINT))
        .addAnnotation(Annotation.create((TODAY + 12) * 1000, SERVER_RECV, WEB_ENDPOINT))
        .addAnnotation(Annotation.create((TODAY + 13) * 1000, SERVER_SEND, WEB_ENDPOINT))
        .addAnnotation(Annotation.create((TODAY + 14) * 1000, CLIENT_RECV, APP_ENDPOINT))
        .addBinaryAnnotation(BinaryAnnotation.create("local", "app", APP_ENDPOINT))
        .addBinaryAnnotation(BinaryAnnotation.create("app-b", "app", APP_ENDPOINT))
        .build();

    accept(trace1, trace2);

    // Sanity check
    assertThat(store().getTrace(trace1.traceIdHigh, trace1.traceId))
        .containsExactly(trace1);
    assertThat(store().getTrace(trace2.traceIdHigh, trace2.traceId))
        .containsExactly(trace2);
    assertThat(store().getTraces(QueryRequest.builder().build()))
         .containsExactly(asList(trace2), asList(trace1));

    // We only return traces where the service specified caused the annotation queried.
    assertThat(store().getTraces(QueryRequest.builder().serviceName("web").addAnnotation("web").build()))
        .containsExactly(asList(trace1));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("app").addAnnotation("web").build()))
        .isEmpty();
    assertThat(store().getTraces(QueryRequest.builder().serviceName("app").addAnnotation("app").build()))
        .containsExactly(asList(trace2));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("web").addAnnotation("app").build()))
        .isEmpty();

    // Binary annotations are returned for annotation queries
    assertThat(store().getTraces(QueryRequest.builder().serviceName("web").addAnnotation("web-b").build()))
        .containsExactly(asList(trace1));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("app").addAnnotation("web-b").build()))
        .isEmpty();
    assertThat(store().getTraces(QueryRequest.builder().serviceName("app").addAnnotation("app-b").build()))
        .containsExactly(asList(trace2));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("web").addAnnotation("app-b").build()))
        .isEmpty();

    // We only return traces where the service specified caused the binary value queried.
    assertThat(store().getTraces(QueryRequest.builder().serviceName("web")
        .addBinaryAnnotation("local", "web").build()))
        .containsExactly(asList(trace1));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("app")
        .addBinaryAnnotation("local", "web").build()))
        .isEmpty();
    assertThat(store().getTraces(QueryRequest.builder().serviceName("app")
        .addBinaryAnnotation("local", "app").build()))
        .containsExactly(asList(trace2));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("web")
        .addBinaryAnnotation("local", "app").build()))
        .isEmpty();
  }

  /** Make sure empty binary annotation values don't crash */
  @Test
  public void getTraces_binaryAnnotationWithEmptyValue() {
    Span span = Span.builder()
        .traceId(1)
        .name("call1")
        .id(1)
        .timestamp((TODAY + 1) * 1000)
        .addBinaryAnnotation(BinaryAnnotation.create("empty", "", ep)).build();

    accept(span);

    assertThat(store().getTraces((QueryRequest.builder().serviceName("service").build())))
        .containsExactly(asList(span));

    assertThat(store().getTrace(span.traceIdHigh, span.traceId))
        .containsExactly(span);
  }

  /** This tests that the 128bit trace id is read back from storage. */
  @Test
  public void getTraces_128BitTraceId() {
    Span span = span1.toBuilder().traceIdHigh(1L).build();

    accept(span);

    assertThat(store().getTraces(QueryRequest.builder().build()))
        .extracting(t -> t.get(0).traceIdHigh)
        .containsExactly(1L);
  }

  /**
   * It is expected that [[com.twitter.zipkin.storage.SpanStore.apply]] will receive the same span
   * id multiple times with different annotations. At query time, these must be merged.
   */
  @Test
  public void getTraces_mergesSpans() {
    accept(span4, span5); // span4, span5 have the same span id

    SortedSet<Annotation> mergedAnnotations = new TreeSet<>(span4.annotations);
    mergedAnnotations.addAll(span5.annotations);

    Span merged = span4.toBuilder()
        .timestamp(mergedAnnotations.first().timestamp)
        .duration(mergedAnnotations.last().timestamp - mergedAnnotations.first().timestamp)
        .annotations(mergedAnnotations)
        .binaryAnnotations(span5.binaryAnnotations).build();

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").build()))
        .containsExactly(asList(merged));
  }

  /** limit should apply to traces closest to endTs */
  @Test
  public void getTraces_limit() {
    accept(span1, span3); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").limit(1).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span3.id);
  }

  /** Traces whose root span has timestamps before or at endTs are returned */
  @Test
  public void getTraces_endTsAndLookback() {
    accept(span1, span3); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").endTs(TODAY + 1L).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span1.id);
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").endTs(TODAY + 2L).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span3.id, span1.id);
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").endTs(TODAY + 3L).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span3.id, span1.id);
  }

  /** Traces whose root span has timestamps between (endTs - lookback) and endTs are returned */
  @Test
  public void getTraces_lookback() {
    accept(span1, span3); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").endTs(TODAY + 1L).lookback(1L).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span1.id);

    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").endTs(TODAY + 2L).lookback(1L).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span3.id, span1.id);
    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").endTs(TODAY + 3L).lookback(1L).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span3.id);
    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").endTs(TODAY + 3L).lookback(2L).build()))
        .extracting(t -> t.get(0).id)
        .containsExactly(span3.id, span1.id);
  }

  @Test
  public void getAllServiceNames_emptyServiceName() {
    accept(spanEmptyServiceName);

    assertThat(store().getServiceNames()).isEmpty();
  }

  @Test
  public void getSpanNames_emptySpanName() {
    accept(spanEmptySpanName);

    assertThat(store().getSpanNames(spanEmptySpanName.name)).isEmpty();
  }

  @Test
  public void spanNamesGoLowercase() {
    accept(span1);

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").spanName("MeThOdCaLl").build()))
        .hasSize(1);
  }

  @Test
  public void serviceNamesGoLowercase() {
    accept(span1);

    assertThat(store().getSpanNames("SeRvIcE")).containsExactly("methodcall");

    assertThat(store().getTraces(QueryRequest.builder().serviceName("SeRvIcE").build()))
        .hasSize(1);
  }

  /**
   * Basic clock skew correction is something span stores should support, until the UI supports
   * happens-before without using timestamps. The easiest clock skew to correct is where a child
   * appears to happen before the parent.
   *
   * <p>It doesn't matter if clock-skew correction happens at store or query time, as long as it
   * occurs by the time results are returned.
   *
   * <p>Span stores who don't support this can override and disable this test, noting in the README
   * the limitation.
   */
  @Test
  public void correctsClockSkew() {
    Endpoint client = Endpoint.create("client", 192 << 24 | 168 << 16 | 1);
    Endpoint frontend = Endpoint.create("frontend", 192 << 24 | 168 << 16 | 2);
    Endpoint backend = Endpoint.create("backend", 192 << 24 | 168 << 16 | 3);

    /** Intentionally not setting span.timestamp, duration */
    Span parent = Span.builder()
        .traceId(1)
        .name("method1")
        .id(666)
        .addAnnotation(Annotation.create((TODAY + 100) * 1000, CLIENT_SEND, client))
        .addAnnotation(Annotation.create((TODAY + 95) * 1000, SERVER_RECV, frontend)) // before client sends
        .addAnnotation(Annotation.create((TODAY + 120) * 1000, SERVER_SEND, frontend)) // before client receives
        .addAnnotation(Annotation.create((TODAY + 135) * 1000, CLIENT_RECV, client)).build();

    /** Intentionally not setting span.timestamp, duration */
    Span remoteChild = Span.builder()
        .traceId(1)
        .name("method2")
        .id(777)
        .parentId(666L)
        .addAnnotation(Annotation.create((TODAY + 100) * 1000, CLIENT_SEND, frontend))
        .addAnnotation(Annotation.create((TODAY + 115) * 1000, SERVER_RECV, backend))
        .addAnnotation(Annotation.create((TODAY + 120) * 1000, SERVER_SEND, backend))
        .addAnnotation(Annotation.create((TODAY + 115) * 1000, CLIENT_RECV, frontend)) // before server sent
        .build();

    /** Local spans must explicitly set timestamp */
    Span localChild = Span.builder()
        .traceId(1)
        .name("local")
        .id(778)
        .parentId(666L)
        .timestamp((TODAY + 101) * 1000).duration(50L)
        .addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "framey", frontend)).build();

    List<Span> skewed = asList(parent, remoteChild, localChild);

    // There's clock skew when the child doesn't happen after the parent
    assertThat(skewed.get(0).annotations.get(0).timestamp)
        .isLessThanOrEqualTo(skewed.get(1).annotations.get(0).timestamp)
        .isLessThanOrEqualTo(skewed.get(2).timestamp); // local span

    // Regardless of when clock skew is corrected, it should be corrected before traces return
    accept(parent, remoteChild, localChild);
    List<Span> adjusted = store().getTrace(localChild.traceIdHigh, localChild.traceId);

    // After correction, the child happens after the parent
    assertThat(adjusted.get(0).timestamp)
        .isLessThanOrEqualTo(adjusted.get(0).timestamp);

    // After correction, children happen after their parent
    assertThat(adjusted.get(0).timestamp)
        .isLessThanOrEqualTo(adjusted.get(1).timestamp)
        .isLessThanOrEqualTo(adjusted.get(2).timestamp);

    // And we do not change the parent (client) duration, due to skew in the child (server)
    assertThat(adjusted.get(0).duration).isEqualTo(clientDuration(skewed.get(0)));
    assertThat(adjusted.get(1).duration).isEqualTo(clientDuration(skewed.get(1)));
    assertThat(adjusted.get(2).duration).isEqualTo(skewed.get(2).duration);
  }

  /**
   * This test shows that regardless of whether span.timestamp and duration are set directly or
   * derived from annotations, the client wins vs the server. This is important because the client
   * holds the critical path of a shared span.
   */
  @Test
  public void clientTimestampAndDurationWinInSharedSpan() {
    Endpoint client = Endpoint.create("client", 192 << 24 | 168 << 16 | 1);
    Endpoint server = Endpoint.create("server", 192 << 24 | 168 << 16 | 2);

    long clientTimestamp = (TODAY + 100) * 1000;
    long clientDuration = 35 * 1000;

    // both client and server set span.timestamp, duration
    Span clientView = Span.builder().traceId(1).name("direct").id(666)
        .timestamp(clientTimestamp).duration(clientDuration)
        .addAnnotation(Annotation.create((TODAY + 100) * 1000, CLIENT_SEND, client))
        .addAnnotation(Annotation.create((TODAY + 135) * 1000, CLIENT_RECV, client))
        .build();

    Span serverView = Span.builder().traceId(1).name("direct").id(666)
        .timestamp((TODAY + 105) * 1000).duration(25 * 1000L)
        .addAnnotation(Annotation.create((TODAY + 105) * 1000, SERVER_RECV, server))
        .addAnnotation(Annotation.create((TODAY + 130) * 1000, SERVER_SEND, server))
        .build();

    // neither client, nor server set span.timestamp, duration
    Span clientViewDerived = Span.builder().traceId(1).name("derived").id(666)
        .addAnnotation(Annotation.create(clientTimestamp, CLIENT_SEND, client))
        .addAnnotation(Annotation.create(clientTimestamp + clientDuration, CLIENT_RECV, client))
        .build();

    Span serverViewDerived = Span.builder().traceId(1).name("derived").id(666)
        .addAnnotation(Annotation.create((TODAY + 105) * 1000, SERVER_RECV, server))
        .addAnnotation(Annotation.create((TODAY + 130) * 1000, SERVER_SEND, server))
        .build();

    accept(serverView, serverViewDerived); // server span hits the collection tier first
    accept(clientView, clientViewDerived); // intentionally different collection event

    for (Span span : store().getTrace(clientView.traceIdHigh, clientView.traceId)) {
      assertThat(span.timestamp).isEqualTo(clientTimestamp);
      assertThat(span.duration).isEqualTo(clientDuration);
    }
  }

  // Bugs have happened in the past where trace limit was mistaken for span count.
  @Test
  public void traceWithManySpans() {
    Span[] trace = new Span[101];
    trace[0] = Span.builder().traceId(0xf66529c8cc356aa0L).id(0x93288b4644570496L).name("get")
      .timestamp(TODAY * 1000).duration(350 * 1000L)
      .addAnnotation(Annotation.create(TODAY * 1000, SERVER_RECV, WEB_ENDPOINT))
      .addAnnotation(Annotation.create((TODAY + 350) * 1000, SERVER_SEND, WEB_ENDPOINT))
      .build();

    IntStream.range(1, trace.length).forEach(i ->
      trace[i] = Span.builder().traceId(trace[0].traceId).parentId(trace[0].id).id(i).name("foo")
        .timestamp((TODAY + i) * 1000).duration(10L)
        .addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "", WEB_ENDPOINT))
        .build());

    accept(trace);

    assertThat(store().getTraces(QueryRequest.builder().build()))
        .containsExactly(asList(trace));
    assertThat(store().getTrace(trace[0].traceIdHigh, trace[0].traceId))
        .containsExactly(trace);
    assertThat(store().getRawTrace(trace[0].traceIdHigh, trace[0].traceId))
        .containsAll(asList(trace)); // order isn't guaranteed in raw trace
  }

  /**
   * Spans report depth-first. Make sure the client timestamp is preferred when instrumentation
   * don't add a timestamp.
   */
  @Test
  public void whenSpanTimestampIsMissingClientSendIsPreferred() {
    Endpoint frontend = Endpoint.create("frontend", 192 << 24 | 168 << 16 | 2);
    Annotation cs = Annotation.create((TODAY + 50) * 1000, CLIENT_SEND, frontend);
    Annotation cr = Annotation.create((TODAY + 150) * 1000, CLIENT_RECV, frontend);

    Endpoint backend = Endpoint.create("backend", 192 << 24 | 168 << 16 | 2);
    Annotation sr = Annotation.create((TODAY + 95) * 1000, SERVER_RECV, backend);
    Annotation ss = Annotation.create((TODAY + 100) * 1000, SERVER_SEND, backend);

    Span span = Span.builder().traceId(1).name("method1").id(666).build();

    // Simulate the server-side of a shared span arriving first
    accept(span.toBuilder().addAnnotation(sr).addAnnotation(ss).build());
    accept(span.toBuilder().addAnnotation(cs).addAnnotation(cr).build());

    // Make sure that the client's timestamp won
    assertThat(store().getTrace(span1.traceIdHigh, span.traceId))
        .containsExactly(span.toBuilder()
            .timestamp(cs.timestamp)
            .duration(cr.timestamp - cs.timestamp)
            .annotations(asList(cs, sr, ss, cr)).build());
  }

  // This supports the "raw trace" feature, which skips application-level data cleaning
  @Test
  public void rawTrace_doesntPerformQueryTimeAdjustment() {
    Endpoint sender = Endpoint.create("sender", 192 << 24 | 168 << 16 | 1);
    Annotation send = Annotation.create((TODAY + 95) * 1000, "send", sender);

    Endpoint receiver = Endpoint.create("receiver", 192 << 24 | 168 << 16 | 2);
    Annotation receive = Annotation.create((TODAY + 100) * 1000, "receive", receiver);

    Span span = Span.builder().traceId(1).name("start").id(666).build();

    // Simulate instrumentation that sends annotations one at-a-time.
    // This should prevent the collection tier from being able to calculate duration.
    accept(span.toBuilder().addAnnotation(send).build());
    accept(span.toBuilder().addAnnotation(receive).build());

    // Normally, span store implementations will merge spans by id and add duration by query time
    assertThat(store().getTrace(span1.traceIdHigh, span.traceId))
        .containsExactly(span.toBuilder()
            .timestamp(send.timestamp)
            .duration(receive.timestamp - send.timestamp)
            .annotations(asList(send, receive)).build());

    // Since a collector never saw both sides of the span, we'd not see duration in the raw trace.
    for (Span raw : store().getRawTrace(span1.traceIdHigh, span.traceId)) {
      assertThat(raw.timestamp).isNull();
      assertThat(raw.duration).isNull();
    }
  }

  @Test public void getTraces_acrossServices() {
    List<BinaryAnnotation> annotations = IntStream.rangeClosed(1, 10).mapToObj(i ->
        BinaryAnnotation.create(LOCAL_COMPONENT, "serviceAnnotation",
            Endpoint.create("service" + i, 127 << 24 | i)))
        .collect(Collectors.toList());

    long gapBetweenSpans = 100;
    List<Span> earlySpans = IntStream.rangeClosed(1, 10).mapToObj(i -> Span.builder().name("early")
        .traceId(i).id(i).timestamp((TODAY - i) * 1000).duration(1L)
        .addBinaryAnnotation(annotations.get(i - 1)).build()).collect(toList());

    List<Span> lateSpans = IntStream.rangeClosed(1, 10).mapToObj(i -> Span.builder().name("late")
        .traceId(i + 10).id(i + 10).timestamp((TODAY + gapBetweenSpans - i) * 1000).duration(1L)
        .addBinaryAnnotation(annotations.get(i - 1)).build()).collect(toList());

    accept(earlySpans.toArray(new Span[10]));
    accept(lateSpans.toArray(new Span[10]));

    List<Span>[] earlyTraces =
        earlySpans.stream().map(Collections::singletonList).toArray(List[]::new);
    List<Span>[] lateTraces =
        lateSpans.stream().map(Collections::singletonList).toArray(List[]::new);

    //sanity checks
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service1").build()))
        .containsExactly(lateTraces[0], earlyTraces[0]);
    assertThat(store().getTraces(QueryRequest.builder().limit(20).build()))
        .hasSize(20);

    assertThat(store().getTraces(QueryRequest.builder().limit(10).build()))
        .containsExactly(lateTraces);

    assertThat(store().getTraces(QueryRequest.builder().limit(20)
        .endTs(TODAY + gapBetweenSpans).lookback(gapBetweenSpans).build()))
        .containsExactly(lateTraces);

    assertThat(store().getTraces(QueryRequest.builder().limit(20)
        .endTs(TODAY).build()))
        .containsExactly(earlyTraces);
  }

  /**
   * Shared server-spans are not supposed to report timestamp, as that interferes with the
   * authoritative timestamp of the caller. This makes sure that server spans can still be looked up
   * when they didn't start a span.
   */
  @Test
  public void traceIsSearchableBySRServiceName() throws Exception {
    Span clientSpan = Span.builder().traceId(20L).id(22L).name("").parentId(21L)
        .addAnnotation(Annotation.create((TODAY - 4) * 1000L, CLIENT_SEND, WEB_ENDPOINT))
        .build();

    Span serverSpan = Span.builder().traceId(20L).id(22L).name("get").parentId(21L)
        .addAnnotation(Annotation.create(TODAY * 1000L, SERVER_RECV, APP_ENDPOINT))
        .build();

    accept(serverSpan, clientSpan);

    List<List<Span>> traces = storage().spanStore().getTraces(
        QueryRequest.builder().serviceName(APP_ENDPOINT.serviceName).build()
    );

    assertThat(traces)
        .hasSize(1) // we can lookup by the server's name
        .flatExtracting(l -> l)
        .extracting(s -> s.timestamp, s -> s.duration)
        .contains(Tuple.tuple((TODAY - 4) * 1000L, 4000L)); // but the client's timestamp wins
  }

  /** Not a good span name, but better to test it than break mysteriously */
  @Test
  public void spanNameIsJson() {
    String json = "{\"foo\":\"bar\"}";
    Span withJsonSpanName = span1.toBuilder().name(json).build();

    accept(withJsonSpanName);

    QueryRequest query = QueryRequest.builder().serviceName(ep.serviceName).spanName(json).build();
    assertThat(store().getTraces(query))
      .extracting(t -> t.get(0).name)
      .containsExactly(json);
  }

  /** Dots in tag names can create problems in storage which tries to make a tree out of them */
  @Test
  public void tagsWithNestedDots() {
    Span tagsWithNestedDots = span1.toBuilder()
      .addBinaryAnnotation(BinaryAnnotation.create("http.path", "/api", ep))
      .addBinaryAnnotation(BinaryAnnotation.create("http.path.morepath", "/api/api", ep))
      .build();

    accept(tagsWithNestedDots);

    assertThat(store().getRawTrace(span1.traceIdHigh, span1.traceId))
      .containsExactly(tagsWithNestedDots);
  }

  static long clientDuration(Span span) {
    long[] timestamps = span.annotations.stream()
        .filter(a -> a.value.startsWith("c"))
        .mapToLong(a -> a.timestamp)
        .sorted().toArray();
    return timestamps[1] - timestamps[0];
  }
}
