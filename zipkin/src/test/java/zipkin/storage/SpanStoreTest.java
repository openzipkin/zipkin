/**
 * Copyright 2015-2016 The OpenZipkin Authors
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;
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
  public abstract void clear();

  /** Notably, the cassandra implementation has day granularity */
  static long midnight(){
    Calendar date = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
    // reset hour, minutes, seconds and millis
    date.set(Calendar.HOUR_OF_DAY, 0);
    date.set(Calendar.MINUTE, 0);
    date.set(Calendar.SECOND, 0);
    date.set(Calendar.MILLISECOND, 0);
    return date.getTimeInMillis();
  }

  // Use real time, as most span-stores have TTL logic which looks back several days.
  long today = midnight();

  Endpoint ep = Endpoint.create("service", 127 << 24 | 1, 8080);

  long spanId = 456;
  Annotation ann1 = Annotation.create((today + 1) * 1000, "cs", ep);
  Annotation ann2 = Annotation.create((today + 2) * 1000, "sr", null);
  Annotation ann3 = Annotation.create((today + 10) * 1000, "custom", ep);
  Annotation ann4 = Annotation.create((today + 20) * 1000, "custom", ep);
  Annotation ann5 = Annotation.create((today + 5) * 1000, "custom", ep);
  Annotation ann6 = Annotation.create((today + 6) * 1000, "custom", ep);
  Annotation ann7 = Annotation.create((today + 7) * 1000, "custom", ep);
  Annotation ann8 = Annotation.create((today + 8) * 1000, "custom", ep);

  Span span1 = Span.builder()
      .traceId(123)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann1.timestamp).duration(9000L)
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
  public void getTrace() {
    accept(span1, span2);
    assertThat(store().getTrace(span1.traceId)).isEqualTo(asList(span1));
  }

  @Test
  public void getTrace_nullWhenNotFound() {
    assertThat(store().getTrace(111111L)).isNull();
  }

  /**
   * Filtered traces are returned in reverse insertion order. This is because the primary search
   * interface is a timeline view, looking back from an end timestamp.
   */
  @Test
  public void tracesRetrieveInOrderDesc() {
    accept(span2, span1.toBuilder().annotations(asList(ann3, ann1)).build());

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").build()))
        .containsOnly(asList(span2), asList(span1));
  }

  /** Legacy instrumentation will not set timestamp and duration explicitly */
  @Test
  public void derivesTimestampAndDurationFromAnnotations() {
    accept(span1.toBuilder().timestamp(null).duration(null).build());

    assertThat(store().getTrace(span1.traceId))
        .containsOnly(span1);
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
  public void getAllServiceNames() {
    BinaryAnnotation yak = BinaryAnnotation.address("sa", Endpoint.create("yak", 127 << 24 | 1, 8080));
    accept(span1.toBuilder().addBinaryAnnotation(yak).build(), span4);

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
          BinaryAnnotation.address("sa", Endpoint.create("yak" + suffix, 127 << 24 | 1, 8080));
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
        .containsExactly(asList(span1));
    assertThat(store().getTraces(q.spanName("methodcall").build()))
        .containsExactly(asList(span1));

    assertThat(store().getTraces(q.spanName("badmethod").build())).isEmpty();
    assertThat(store().getTraces(q.serviceName("badservice").build())).isEmpty();
    assertThat(store().getTraces(q.spanName(null).build())).isEmpty();
  }

  @Test
  public void getTraces_serviceNameInBinaryAnnotation() {
    Span localTrace = Span.builder().traceId(1L).name("targz").id(1L)
        .timestamp(today * 1000 + 100L).duration(200L)
        .addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "archiver", ep)).build();

    accept(localTrace);

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").build()))
        .containsExactly(asList(localTrace));
  }

  /** Shows that duration queries go against the root span, not the child */
  @Test
  public void getTraces_duration() {
    Endpoint service1 = Endpoint.create("service1", 127 << 24 | 1, 8080);
    Endpoint service2 = Endpoint.create("service2", 127 << 24 | 2, 8080);
    Endpoint service3 = Endpoint.create("service3", 127 << 24 | 3, 8080);

    BinaryAnnotation.Builder component = BinaryAnnotation.builder().key(LOCAL_COMPONENT).value("archiver");
    BinaryAnnotation archiver1 = component.endpoint(service1).build();
    BinaryAnnotation archiver2 = component.endpoint(service2).build();
    BinaryAnnotation archiver3 = component.endpoint(service3).build();

    Span targz = Span.builder().traceId(1L).id(1L)
        .name("targz").timestamp(today * 1000 + 100L).duration(200L).addBinaryAnnotation(archiver1).build();
    Span tar = Span.builder().traceId(1L).id(2L).parentId(1L)
        .name("tar").timestamp(today * 1000 + 200L).duration(150L).addBinaryAnnotation(archiver2).build();
    Span gz = Span.builder().traceId(1L).id(3L).parentId(1L)
        .name("gz").timestamp(today * 1000 + 250L).duration(50L).addBinaryAnnotation(archiver3).build();
    Span zip = Span.builder().traceId(3L).id(3L)
        .name("zip").timestamp(today * 1000 + 130L).duration(50L).addBinaryAnnotation(archiver2).build();

    List<Span> trace1 = asList(targz, tar, gz);
    List<Span> trace2 = asList(
        targz.toBuilder().traceId(2L).timestamp(today * 1000 + 110L).binaryAnnotations(asList(archiver3)).build(),
        tar.toBuilder().traceId(2L).timestamp(today * 1000 + 210L).binaryAnnotations(asList(archiver2)).build(),
        gz.toBuilder().traceId(2L).timestamp(today * 1000 + 260L).binaryAnnotations(asList(archiver1)).build());
    List<Span> trace3 = asList(zip);

    accept(trace1.toArray(new Span[0]));
    accept(trace2.toArray(new Span[0]));
    accept(trace3.toArray(new Span[0]));

    long lookback = 12L * 60 * 60 * 1000; // 12hrs, instead of 7days
    long endTs = today + 1; // greater than all timestamps above
    QueryRequest.Builder q = QueryRequest.builder().serviceName("service1").lookback(lookback).endTs(endTs);

    // Min duration is inclusive and is applied by service.
    assertThat(store().getTraces(q.serviceName("service1").minDuration(targz.duration).build()))
        .containsExactly(trace1);

    assertThat(store().getTraces(q.serviceName("service3").minDuration(targz.duration).build()))
        .containsExactly(trace2);

    // Duration bounds aren't limited to root spans: they apply to all spans by service in a trace
    assertThat(store().getTraces(q.serviceName("service2").minDuration(zip.duration).maxDuration(tar.duration).build()))
        .containsExactly(trace3, trace2, trace1); // service2 is in the middle of trace1 and 2, but root of trace3

    // Span name should apply to the duration filter
    assertThat(
        store().getTraces(q.serviceName("service2").spanName("zip").maxDuration(zip.duration).build()))
        .containsExactly(trace3);

    // Max duration should filter our longer spans from the same service
    assertThat(store().getTraces(q.serviceName("service2").minDuration(gz.duration).maxDuration(zip.duration).build()))
        .containsExactly(trace3);
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
        .containsExactly(asList(span1));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").spanName("methodcall").build()))
        .containsExactly(asList(span1));
  }

  @Test
  public void getTraces_annotation() {
    accept(span1);

    // fetch by time based annotation, find trace
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("custom").build()))
        .containsExactly(asList(span1));

    // should find traces by the key and value annotation
    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").addBinaryAnnotation("BAH", "BEH").build()))
        .containsExactly(asList(span1));
  }

  @Test
  public void getTraces_multipleAnnotationsBecomeAndFilter() {
    Span foo = Span.builder().traceId(1).name("call1").id(1)
        .timestamp((today + 1) * 1000)
        .addAnnotation(Annotation.create((today + 1) * 1000, "foo", ep)).build();
    // would be foo bar, except lexicographically bar precedes foo
    Span barAndFoo = Span.builder().traceId(2).name("call2").id(2)
        .timestamp((today + 2) * 1000)
        .addAnnotation(Annotation.create((today + 2) * 1000, "bar", ep))
        .addAnnotation(Annotation.create((today + 2) * 1000, "foo", ep)).build();
    Span fooAndBazAndQux = Span.builder().traceId(3).name("call3").id(3)
        .timestamp((today + 3) * 1000)
        .addAnnotation(Annotation.create((today + 3) * 1000, "foo", ep))
        .addBinaryAnnotation(BinaryAnnotation.create("baz", "qux", ep))
        .build();
    Span barAndFooAndBazAndQux = Span.builder().traceId(4).name("call4").id(4)
        .timestamp((today + 4) * 1000)
        .addAnnotation(Annotation.create((today + 4) * 1000, "bar", ep))
        .addAnnotation(Annotation.create((today + 4) * 1000, "foo", ep))
        .addBinaryAnnotation(BinaryAnnotation.create("baz", "qux", ep))
        .build();

    accept(foo, barAndFoo, fooAndBazAndQux, barAndFooAndBazAndQux);

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("foo").build()))
        .containsExactly(asList(barAndFooAndBazAndQux), asList(fooAndBazAndQux), asList(barAndFoo), asList(foo));

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("foo").addAnnotation("bar").build()))
        .containsExactly(asList(barAndFooAndBazAndQux), asList(barAndFoo));

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").addAnnotation("foo").addAnnotation("bar").addBinaryAnnotation("baz", "qux").build()))
        .containsExactly(asList(barAndFooAndBazAndQux));
  }

  /** Make sure empty binary annotation values don't crash */
  @Test
  public void getTraces_binaryAnnotationWithEmptyValue() {
    Span span = Span.builder()
        .traceId(1)
        .name("call1")
        .id(1)
        .timestamp((today + 1) * 1000)
        .addBinaryAnnotation(BinaryAnnotation.create("empty", "", ep)).build();

    accept(span);

    assertThat(store().getTraces((QueryRequest.builder().serviceName("service").build())))
        .containsExactly(asList(span));

    assertThat(store().getTrace(1L))
        .containsExactly(span);
  }

  /**
   * It is expected that [[com.twitter.zipkin.storage.SpanStore.apply]] will receive the same span
   * id multiple times with different annotations. At query time, these must be merged.
   */
  @Test
  public void getTraces_mergesSpans() {
    accept(span1, span4, span5); // span4, span5 have the same span id

    SortedSet<Annotation> mergedAnnotations = new TreeSet<>(span4.annotations);
    mergedAnnotations.addAll(span5.annotations);

    Span merged = span4.toBuilder()
        .timestamp(mergedAnnotations.first().timestamp)
        .duration(mergedAnnotations.last().timestamp - mergedAnnotations.first().timestamp)
        .annotations(mergedAnnotations)
        .binaryAnnotations(span5.binaryAnnotations).build();

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").build()))
        .containsExactly(asList(merged), asList(span1));
  }

  /** limit should apply to traces closest to endTs */
  @Test
  public void getTraces_limit() {
    accept(span1, span3); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").limit(1).build()))
        .containsExactly(asList(span3));
  }

  /** Traces whose root span has timestamps before or at endTs are returned */
  @Test
  public void getTraces_endTsAndLookback() {
    accept(span1, span3); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").endTs(today + 1L).build()))
        .containsExactly(asList(span1));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").endTs(today + 2L).build()))
        .containsExactly(asList(span3), asList(span1));
    assertThat(store().getTraces(QueryRequest.builder().serviceName("service").endTs(today + 3L).build()))
        .containsExactly(asList(span3), asList(span1));
  }

  /** Traces whose root span has timestamps between (endTs - lookback) and endTs are returned */
  @Test
  public void getTraces_lookback() {
    accept(span1, span3); // span1's timestamp is 1000, span3's timestamp is 2000

    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").endTs(today + 1L).lookback(1L).build()))
        .containsExactly(asList(span1));
    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").endTs(today + 2L).lookback(1L).build()))
        .containsExactly(asList(span3), asList(span1));
    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").endTs(today + 3L).lookback(1L).build()))
        .containsExactly(asList(span3));
    assertThat(
        store().getTraces(QueryRequest.builder().serviceName("service").endTs(today + 3L).lookback(2L).build()))
        .containsExactly(asList(span3), asList(span1));
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
        .containsOnly(asList(span1));
  }

  @Test
  public void serviceNamesGoLowercase() {
    accept(span1);

    assertThat(store().getSpanNames("SeRvIcE")).containsExactly("methodcall");

    assertThat(store().getTraces(QueryRequest.builder().serviceName("SeRvIcE").build()))
        .containsOnly(asList(span1));
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
    Endpoint client = Endpoint.create("client", 192 << 24 | 168 << 16 | 1, 8080);
    Endpoint frontend = Endpoint.create("frontend", 192 << 24 | 168 << 16 | 2, 8080);
    Endpoint backend = Endpoint.create("backend", 192 << 24 | 168 << 16 | 3, 8080);

    /** Intentionally not setting span.timestamp, duration */
    Span parent = Span.builder()
        .traceId(1)
        .name("method1")
        .id(666)
        .addAnnotation(Annotation.create((today + 100) * 1000, CLIENT_SEND, client))
        .addAnnotation(Annotation.create((today + 95) * 1000, SERVER_RECV, frontend)) // before client sends
        .addAnnotation(Annotation.create((today + 120) * 1000, SERVER_SEND, frontend)) // before client receives
        .addAnnotation(Annotation.create((today + 135) * 1000, CLIENT_RECV, client)).build();

    /** Intentionally not setting span.timestamp, duration */
    Span remoteChild = Span.builder()
        .traceId(1)
        .name("method2")
        .id(777)
        .parentId(666L)
        .addAnnotation(Annotation.create((today + 100) * 1000, CLIENT_SEND, frontend))
        .addAnnotation(Annotation.create((today + 115) * 1000, SERVER_RECV, backend))
        .addAnnotation(Annotation.create((today + 120) * 1000, SERVER_SEND, backend))
        .addAnnotation(Annotation.create((today + 115) * 1000, CLIENT_RECV, frontend)) // before server sent
        .build();

    /** Local spans must explicitly set timestamp */
    Span localChild = Span.builder()
        .traceId(1)
        .name("local")
        .id(778)
        .parentId(666L)
        .timestamp((today + 101) * 1000).duration(50L)
        .addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "framey", frontend)).build();

    List<Span> skewed = asList(parent, remoteChild, localChild);

    // There's clock skew when the child doesn't happen after the parent
    assertThat(skewed.get(0).annotations.get(0).timestamp)
        .isLessThanOrEqualTo(skewed.get(1).annotations.get(0).timestamp)
        .isLessThanOrEqualTo(skewed.get(2).timestamp); // local span

    // Regardless of when clock skew is corrected, it should be corrected before traces return
    accept(parent, remoteChild, localChild);
    List<Span> adjusted = store().getTrace(1L);

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
    Endpoint client = Endpoint.create("client", 192 << 24 | 168 << 16 | 1, 8080);
    Endpoint server = Endpoint.create("server", 192 << 24 | 168 << 16 | 2, 8080);

    long clientTimestamp = (today + 100) * 1000;
    long clientDuration = 35 * 1000;

    // both client and server set span.timestamp, duration
    Span clientView = Span.builder().traceId(1).name("direct").id(666)
        .timestamp(clientTimestamp).duration(clientDuration)
        .addAnnotation(Annotation.create((today + 100) * 1000, CLIENT_SEND, client))
        .addAnnotation(Annotation.create((today + 135) * 1000, CLIENT_RECV, client))
        .build();

    Span serverView = Span.builder().traceId(1).name("direct").id(666)
        .timestamp((today + 105) * 1000).duration(25 * 1000L)
        .addAnnotation(Annotation.create((today + 105) * 1000, SERVER_RECV, server))
        .addAnnotation(Annotation.create((today + 130) * 1000, SERVER_SEND, server))
        .build();

    // neither client, nor server set span.timestamp, duration
    Span clientViewDerived = Span.builder().traceId(1).name("derived").id(666)
        .addAnnotation(Annotation.create(clientTimestamp, CLIENT_SEND, client))
        .addAnnotation(Annotation.create(clientTimestamp + clientDuration, CLIENT_SEND, client))
        .build();

    Span serverViewDerived = Span.builder().traceId(1).name("derived").id(666)
        .addAnnotation(Annotation.create((today + 105) * 1000, SERVER_RECV, server))
        .addAnnotation(Annotation.create((today + 130) * 1000, SERVER_SEND, server))
        .build();

    accept(serverView, serverViewDerived); // server span hits the collection tier first
    accept(clientView, clientViewDerived); // intentionally different collection event

    for (Span span : store().getTrace(clientView.traceId)) {
      assertThat(span.timestamp).isEqualTo(clientTimestamp);
      assertThat(span.duration).isEqualTo(clientDuration);
    }
  }

  // Bugs have happened in the past where trace limit was mistaken for span count.
  @Test
  public void traceWithManySpans() {
    Span[] trace = new Span[101];
    trace[0] = TestObjects.TRACE.get(0);

    IntStream.range(0, 100).forEach(i -> {
      Span s = TestObjects.TRACE.get(1);
      trace[i + 1] = s.toBuilder()
          .id(s.id + i)
          .timestamp(s.timestamp + i)
          .annotations(s.annotations.stream()
              .map(a -> Annotation.create(a.timestamp + i, a.value, a.endpoint))
              .collect(toList()))
          .build();
    });

    accept(trace);

    String serviceName = trace[1].annotations.get(0).endpoint.serviceName;
    assertThat(store().getTraces(QueryRequest.builder().serviceName(serviceName).build()))
        .containsExactly(asList(trace));
    assertThat(store().getTrace(trace[0].traceId))
        .containsExactly(trace);
    assertThat(store().getRawTrace(trace[0].traceId))
        .containsAll(asList(trace)); // order isn't guaranteed in raw trace
  }

  // This supports the "raw trace" feature, which skips application-level data cleaning
  @Test
  public void rawTrace_doesntPerformQueryTimeAdjustment() {
    Endpoint frontend = Endpoint.create("frontend", 192 << 24 | 168 << 16 | 2, 8080);
    Annotation sr = Annotation.create((today + 95) * 1000, SERVER_RECV, frontend);
    Annotation ss = Annotation.create((today + 100) * 1000, SERVER_SEND, frontend);

    Span span = Span.builder().traceId(1).name("method1").id(666).build();

    // Simulate instrumentation that sends annotations one at-a-time.
    // This should prevent the collection tier from being able to calculate duration.
    accept(span.toBuilder().addAnnotation(sr).build());
    accept(span.toBuilder().addAnnotation(ss).build());

    // Normally, span store implementations will merge spans by id and add duration by query time
    assertThat(store().getTrace(span.traceId))
        .containsExactly(span.toBuilder()
            .timestamp(sr.timestamp)
            .duration(ss.timestamp - sr.timestamp)
            .annotations(asList(sr, ss)).build());

    // Since a collector never saw both sides of the span, we'd not see duration in the raw trace.
    for (Span raw : store().getRawTrace(span.traceId)) {
      assertThat(raw.timestamp).isNull();
      assertThat(raw.duration).isNull();
    }
  }

  @Test public void getTraces_acrossServices() {
    List<BinaryAnnotation> annotations = IntStream.rangeClosed(1, 10).mapToObj(i ->
        BinaryAnnotation.create(LOCAL_COMPONENT, "serviceAnnotation",
            Endpoint.create("service" + i, 127 << 24 | i, 8080)))
        .collect(Collectors.toList());

    long gapBetweenSpans = 100;
    List<Span> earlySpans = IntStream.rangeClosed(1, 10).mapToObj(i -> Span.builder().name("early")
        .traceId(i).id(i).timestamp((today - i) * 1000).duration(1L)
        .addBinaryAnnotation(annotations.get(i - 1)).build()).collect(toList());

    List<Span> lateSpans = IntStream.rangeClosed(1, 10).mapToObj(i -> Span.builder().name("late")
        .traceId(i + 10).id(i + 10).timestamp((today + gapBetweenSpans - i) * 1000).duration(1L)
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
        .endTs(today + gapBetweenSpans).lookback(gapBetweenSpans).build()))
        .containsExactly(lateTraces);

    assertThat(store().getTraces(QueryRequest.builder().limit(20)
        .endTs(today).build()))
        .containsExactly(earlyTraces);
  }

  static long clientDuration(Span span) {
    long[] timestamps = span.annotations.stream()
        .filter(a -> a.value.startsWith("c"))
        .mapToLong(a -> a.timestamp)
        .sorted().toArray();
    return timestamps[1] - timestamps[0];
  }
}
