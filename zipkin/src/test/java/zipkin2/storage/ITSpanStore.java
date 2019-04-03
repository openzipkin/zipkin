/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Trace;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.BACKEND;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.TRACE;
import static zipkin2.TestObjects.TRACE_STARTTS;

/**
 * Base test for {@link SpanStore}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 */
public abstract class ITSpanStore {

  /** Should maintain state between multiple calls within a test. */
  protected abstract StorageComponent storage();

  protected SpanStore store() {
    return storage().spanStore();
  }

  /** Clears store between tests. */
  @Before public abstract void clear() throws Exception;

  @Test public void getTrace_considersBitsAbove64bit() throws IOException {
    // 64-bit trace ID
    Span span1 = Span.newBuilder().traceId(CLIENT_SPAN.traceId().substring(16)).id("1").build();
    // 128-bit trace ID prefixed by above
    Span span2 = Span.newBuilder().traceId(CLIENT_SPAN.traceId()).id("2").build();
    // Different 128-bit trace ID prefixed by above
    Span span3 = Span.newBuilder().traceId("1" + span1.traceId()).id("3").build();

    accept(span1, span2, span3);

    for (Span span : Arrays.asList(span1, span2, span3)) {
      assertThat(store().getTrace(span.traceId()).execute())
        .containsOnly(span);
    }
  }

  @Test public void getTrace_returnsEmptyOnNotFound() throws IOException {
    assertThat(store().getTrace(CLIENT_SPAN.traceId()).execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(store().getTrace(CLIENT_SPAN.traceId()).execute())
      .containsExactly(CLIENT_SPAN);

    assertThat(store().getTrace(CLIENT_SPAN.traceId().substring(16)).execute())
      .isEmpty();
  }

  /** This would only happen when the store layer is bootstrapping, or has been purged. */
  @Test public void allShouldWorkWhenEmpty() throws IOException {
    QueryRequest.Builder q = requestBuilder().serviceName("service");
    assertThat(store().getTraces(q.build()).execute()).isEmpty();
    assertThat(store().getTraces(q.spanName("methodcall").build()).execute()).isEmpty();
    assertThat(store().getTraces(q.parseAnnotationQuery("custom").build()).execute()).isEmpty();
    assertThat(store().getTraces(q.parseAnnotationQuery("BAH=BEH").build()).execute()).isEmpty();
  }

  /** This is unlikely and means instrumentation sends empty spans by mistake. */
  @Test public void allShouldWorkWhenNoIndexableDataYet() throws IOException {
    accept(Span.newBuilder().traceId("1").id("1").build());

    allShouldWorkWhenEmpty();
  }

  /**
   * Ideally, storage backends can deduplicate identical documents as this will prevent some
   * analysis problems such as double-counting dependency links or other statistics. While this test
   * exists, it is known not all backends will be able to cheaply make it pass. In other words, it
   * is optional.
   */
  @Test public void deduplicates() throws IOException {
    // simulate a re-processed message
    accept(LOTS_OF_SPANS[0]);
    accept(LOTS_OF_SPANS[0]);

    assertThat(sortTrace(store().getTrace(LOTS_OF_SPANS[0].traceId()).execute()))
      .containsExactly(LOTS_OF_SPANS[0]);
  }

  @Test public void getTraces_groupsTracesTogether() throws IOException {
    Span traceASpan1 = Span.newBuilder()
      .traceId("a")
      .id("1")
      .timestamp((TODAY + 1) * 1000L)
      .localEndpoint(FRONTEND)
      .build();
    Span traceASpan2 = traceASpan1.toBuilder().id("2").timestamp((TODAY + 2) * 1000L).build();
    Span traceBSpan1 = traceASpan1.toBuilder().traceId("b").build();
    Span traceBSpan2 = traceASpan2.toBuilder().traceId("b").build();

    accept(traceASpan1, traceBSpan1, traceASpan2, traceBSpan2);

    assertThat(sortTraces(store().getTraces(requestBuilder().build()).execute()))
      .containsExactlyInAnyOrder(asList(traceASpan1, traceASpan2),
        asList(traceBSpan1, traceBSpan2));
  }

  @Test public void getTraces_considersBitsAbove64bit() throws IOException {
    // 64-bit trace ID
    Span span1 = Span.newBuilder().traceId(CLIENT_SPAN.traceId().substring(16)).id("1")
      .putTag("foo", "1")
      .timestamp(TODAY * 1000L)
      .localEndpoint(FRONTEND)
      .build();
    // 128-bit trace ID prefixed by above
    Span span2 = span1.toBuilder().traceId(CLIENT_SPAN.traceId()).putTag("foo", "2").build();
    // Different 128-bit trace ID prefixed by above
    Span span3 = span1.toBuilder().traceId("1" + span1.traceId()).putTag("foo", "3").build();

    accept(span1, span2, span3);

    for (Span span : Arrays.asList(span1, span2, span3)) {
      assertThat(store().getTraces(requestBuilder()
        .serviceName("frontend")
        .parseAnnotationQuery("foo=" + span.tags().get("foo")).build()
      ).execute()).flatExtracting(t -> t).containsExactly(span);
    }
  }

  @Test public void getTraces_filteringMatchesMostRecentTraces() throws Exception {
    List<Endpoint> endpoints = IntStream.rangeClosed(1, 10)
      .mapToObj(i -> Endpoint.newBuilder().serviceName("service" + i).ip("127.0.0.1").build())
      .collect(Collectors.toList());

    long gapBetweenSpans = 100;
    Span[] earlySpans =
      IntStream.rangeClosed(1, 10).mapToObj(i -> Span.newBuilder().name("early")
        .traceId(Integer.toHexString(i)).id(Integer.toHexString(i))
        .timestamp((TODAY - i) * 1000L).duration(1L)
        .localEndpoint(endpoints.get(i - 1)).build()).toArray(Span[]::new);

    Span[] lateSpans = IntStream.rangeClosed(1, 10).mapToObj(i -> Span.newBuilder().name("late")
      .traceId(Integer.toHexString(i + 10)).id(Integer.toHexString(i + 10))
      .timestamp((TODAY + gapBetweenSpans - i) * 1000L).duration(1L)
      .localEndpoint(endpoints.get(i - 1)).build()).toArray(Span[]::new);

    accept(earlySpans);
    accept(lateSpans);

    List<Span>[] earlyTraces =
      Stream.of(earlySpans).map(Collections::singletonList).toArray(List[]::new);
    List<Span>[] lateTraces =
      Stream.of(lateSpans).map(Collections::singletonList).toArray(List[]::new);

    assertThat(store().getTraces(requestBuilder().build()).execute())
      .hasSize(20);

    assertThat(sortTraces(store().getTraces(requestBuilder()
      .limit(10).build()).execute()))
      .containsExactly(lateTraces);

    assertThat(sortTraces(store().getTraces(requestBuilder()
      .endTs(TODAY + gapBetweenSpans).lookback(gapBetweenSpans).build()).execute()))
      .containsExactly(lateTraces);

    assertThat(sortTraces(store().getTraces(requestBuilder()
      .endTs(TODAY).build()).execute()))
      .containsExactly(earlyTraces);
  }

  @Test public void getTraces_localServiceName() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(requestBuilder()
      .serviceName("frontend" + 1)
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .serviceName("frontend")
      .build()).execute()).flatExtracting(l -> l).contains(CLIENT_SPAN);
  }

  @Test public void getTraces_spanName() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(requestBuilder()
      .spanName(CLIENT_SPAN.name() + 1)
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .spanName(CLIENT_SPAN.name())
      .build()).execute()).flatExtracting(l -> l).contains(CLIENT_SPAN);

    assertThat(store().getTraces(requestBuilder()
      .spanName("frontend")
      .spanName(CLIENT_SPAN.name())
      .build()).execute()).flatExtracting(l -> l).contains(CLIENT_SPAN);
  }

  @Test public void getTraces_spanName_128() throws Exception {
    // add a trace with the same trace ID truncated to 64 bits, except the span name.
    accept(CLIENT_SPAN.toBuilder()
      .traceId(CLIENT_SPAN.traceId().substring(16))
      .name("bar")
      .build());

    getTraces_spanName();
  }

  @Test public void getTraces_tags() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(requestBuilder()
      .annotationQuery(Collections.singletonMap("foo", "bar"))
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .annotationQuery(CLIENT_SPAN.tags())
      .build()).execute()).flatExtracting(l -> l).contains(CLIENT_SPAN);
  }

  @Test public void getTraces_minDuration() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.durationAsLong() + 1)
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.durationAsLong())
      .build()).execute()).flatExtracting(l -> l).contains(CLIENT_SPAN);
  }

  // pretend we had a late update of only timestamp/duration info
  @Test public void getTraces_lateDuration() throws Exception {
    Span missingDuration = CLIENT_SPAN.toBuilder().duration(0L).build();
    Span lateDuration = Span.newBuilder()
      .traceId(CLIENT_SPAN.traceId())
      .id(CLIENT_SPAN.id())
      .timestamp(CLIENT_SPAN.timestampAsLong())
      .duration(CLIENT_SPAN.durationAsLong())
      .localEndpoint(CLIENT_SPAN.localEndpoint())
      .build();
    accept(missingDuration);
    accept(lateDuration);

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.durationAsLong() + 1)
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.durationAsLong())
      .build()).execute()).flatExtracting(Trace::merge).containsExactly(CLIENT_SPAN);
  }

  @Test public void getTraces_maxDuration() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.durationAsLong() - 2)
      .maxDuration(CLIENT_SPAN.durationAsLong() - 1)
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.durationAsLong())
      .maxDuration(CLIENT_SPAN.durationAsLong())
      .build()).execute()).flatExtracting(l -> l).contains(CLIENT_SPAN);
  }

  /**
   * The following skeletal span is used in dependency linking.
   *
   * <p>Notably this guards empty tag values work
   */
  @Test public void readback_minimalErrorSpan() throws Exception {
    String serviceName = "isao01";
    Span errorSpan = Span.newBuilder()
      .traceId("dc955a1d4768875d")
      .id("dc955a1d4768875d")
      .timestamp(TODAY * 1000L)
      .localEndpoint(Endpoint.newBuilder().serviceName(serviceName).build())
      .kind(Span.Kind.CLIENT)
      .putTag("error", "")
      .build();
    accept(errorSpan);

    QueryRequest.Builder requestBuilder =
      requestBuilder().serviceName(serviceName); // so this doesn't die on cassandra v1

    assertThat(store().getTraces(requestBuilder.build()).execute())
      .flatExtracting(l -> l).contains(errorSpan);

    assertThat(store().getTraces(requestBuilder.parseAnnotationQuery("error").build()).execute())
      .flatExtracting(l -> l).contains(errorSpan);
    assertThat(store().getTraces(requestBuilder.parseAnnotationQuery("error=1").build()).execute())
      .isEmpty();

    assertThat(store().getTrace(errorSpan.traceId()).execute())
      .contains(errorSpan);
  }

  /**
   * While large spans are discouraged, and maybe not indexed, we should be able to read them back.
   */
  @Test public void readsBackLargeValues() throws IOException {
    char[] kilobyteOfText = new char[1024];
    Arrays.fill(kilobyteOfText, 'a');

    // Make a span that's over 1KiB in size
    Span span = Span.newBuilder().traceId("1").id("1").name("big")
      .timestamp(TODAY * 1000L + 100L).duration(200L)
      .localEndpoint(FRONTEND)
      .putTag("a", new String(kilobyteOfText)).build();

    accept(span);

    // read back to ensure the data wasn't truncated
    assertThat(store().getTraces(requestBuilder().build()).execute())
      .containsExactly(asList(span));
    assertThat(store().getTrace(span.traceId()).execute())
      .containsExactly(span);
  }

  /** Not a good span name, but better to test it than break mysteriously */
  @Test public void spanNameIsJson() throws IOException {
    String json = "{\"foo\":\"bar\"}";
    Span withJsonSpanName = CLIENT_SPAN.toBuilder().name(json).build();

    accept(withJsonSpanName);

    QueryRequest query = requestBuilder().serviceName("frontend").spanName(json).build();
    assertThat(store().getTraces(query).execute())
      .extracting(t -> t.get(0).name())
      .containsExactly(json);
  }

  /** Dots in tag names can create problems in storage which tries to make a tree out of them */
  @Test public void tagsWithNestedDots() throws IOException {
    Span tagsWithNestedDots = CLIENT_SPAN.toBuilder()
      .putTag("http.path", "/api")
      .putTag("http.path.morepath", "/api/api")
      .build();

    accept(tagsWithNestedDots);

    assertThat(store().getTrace(tagsWithNestedDots.traceId()).execute())
      .containsExactly(tagsWithNestedDots);
  }

  /**
   * Formerly, a bug was present where cassandra didn't index more than bucket count traces per
   * millisecond. This stores a lot of spans to ensure indexes work under high-traffic scenarios.
   */
  @Test public void getTraces_manyTraces() throws IOException {
    int traceCount = 1000;
    Span span = LOTS_OF_SPANS[0];
    Map.Entry<String, String> tag = span.tags().entrySet().iterator().next();

    accept(Arrays.copyOfRange(LOTS_OF_SPANS, 0, traceCount));

    assertThat(store().getTraces(requestBuilder().limit(traceCount).build()).execute())
      .hasSize(traceCount);

    QueryRequest.Builder builder =
      requestBuilder().limit(traceCount).serviceName(span.localServiceName());

    assertThat(store().getTraces(builder.build()).execute())
      .hasSize(traceCount);

    assertThat(store().getTraces(builder.spanName(span.name()).build()).execute())
      .hasSize(traceCount);

    assertThat(
      store().getTraces(builder.parseAnnotationQuery(tag.getKey() + "=" + tag.getValue()).build())
        .execute())
      .hasSize(traceCount);
  }

  /** Shows that duration queries go against the root span, not the child */
  @Test public void getTraces_duration() throws IOException {
    setupDurationData();

    QueryRequest.Builder q = requestBuilder().endTs(TODAY).lookback(DAY); // instead of since epoch
    QueryRequest query;

    // Min duration is inclusive and is applied by service.
    query = q.serviceName("service1").minDuration(200_000L).build();
    assertThat(store().getTraces(query).execute()).extracting(t -> t.get(0).traceId())
      .containsExactly("0000000000000001");

    query = q.serviceName("service3").minDuration(200_000L).build();
    assertThat(store().getTraces(query).execute()).extracting(t -> t.get(0).traceId())
      .containsExactly("0000000000000002");

    // Duration bounds aren't limited to root spans: they apply to all spans by service in a trace
    query = q.serviceName("service2").minDuration(50_000L).maxDuration(150_000L).build();
    assertThat(store().getTraces(query).execute()).extracting(t -> t.get(0).traceId())
      // service2 root of trace 3, but middle of 1 and 2.
      .containsExactlyInAnyOrder("0000000000000003", "0000000000000002", "0000000000000001");

    // Span name should apply to the duration filter
    query = q.serviceName("service2").spanName("zip").maxDuration(50_000L).build();
    assertThat(store().getTraces(query).execute()).extracting(t -> t.get(0).traceId())
      .containsExactly("0000000000000003");

    // Max duration should filter our longer spans from the same service
    query = q.serviceName("service2").minDuration(50_000L).maxDuration(50_000L).build();
    assertThat(store().getTraces(query).execute()).extracting(t -> t.get(0).traceId())
      .containsExactly("0000000000000003");
  }

  /**
   * Spans and traces are meaningless unless they have a timestamp. While unlikely, this could
   * happen if a binary annotation is logged before a timestamped one is.
   */
  @Test public void getTraces_absentWhenNoTimestamp() throws IOException {
    // Index the service name but no timestamp of any sort
    accept(Span.newBuilder()
      .traceId(CLIENT_SPAN.traceId())
      .id(CLIENT_SPAN.id())
      .name(CLIENT_SPAN.name())
      .localEndpoint(CLIENT_SPAN.localEndpoint())
      .build()
    );

    assertThat(store().getTraces(
      requestBuilder().serviceName("frontend").build()
    ).execute()).isEmpty();

    assertThat(store().getTraces(
      requestBuilder()
        .serviceName("frontend")
        .spanName(CLIENT_SPAN.name())
        .build()
    ).execute()).isEmpty();

    // now store the timestamped span
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(
      requestBuilder().serviceName("frontend").build()
    ).execute()).isNotEmpty();

    assertThat(store().getTraces(
      requestBuilder()
        .serviceName("frontend")
        .spanName(CLIENT_SPAN.name())
        .build()
    ).execute()).isNotEmpty();
  }

  @Test public void getTraces_annotation() throws IOException {
    accept(CLIENT_SPAN);

    // fetch by time based annotation, find trace
    assertThat(store().getTraces(
      requestBuilder()
        .serviceName("frontend")
        .parseAnnotationQuery(CLIENT_SPAN.annotations().get(0).value())
        .build()
    ).execute()).isNotEmpty();

    // should find traces by a tag
    Map.Entry<String, String> tag = CLIENT_SPAN.tags().entrySet().iterator().next();
    assertThat(store().getTraces(
      requestBuilder()
        .serviceName("frontend")
        .parseAnnotationQuery(tag.getKey() + "=" + tag.getValue())
        .build()
    ).execute()).isNotEmpty();
  }

  @Test public void getTraces_multipleAnnotationsBecomeAndFilter() throws IOException {
    Span foo = Span.newBuilder().traceId("1").name("call1").id(1)
      .timestamp((TODAY + 1) * 1000L)
      .localEndpoint(FRONTEND)
      .addAnnotation((TODAY + 1) * 1000L, "foo").build();
    // would be foo bar, except lexicographically bar precedes foo
    Span barAndFoo = Span.newBuilder().traceId("2").name("call2").id(2)
      .timestamp((TODAY + 2) * 1000L)
      .localEndpoint(FRONTEND)
      .addAnnotation((TODAY + 2) * 1000L, "bar")
      .addAnnotation((TODAY + 2) * 1000L, "foo").build();
    Span fooAndBazAndQux = Span.newBuilder().traceId("3").name("call3").id(3)
      .timestamp((TODAY + 3) * 1000L)
      .localEndpoint(FRONTEND)
      .addAnnotation((TODAY + 3) * 1000L, "foo")
      .putTag("baz", "qux")
      .build();
    Span barAndFooAndBazAndQux = Span.newBuilder().traceId("4").name("call4").id(4)
      .timestamp((TODAY + 4) * 1000L)
      .localEndpoint(FRONTEND)
      .addAnnotation((TODAY + 4) * 1000L, "bar")
      .addAnnotation((TODAY + 4) * 1000L, "foo")
      .putTag("baz", "qux")
      .build();

    accept(foo, barAndFoo, fooAndBazAndQux, barAndFooAndBazAndQux);

    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("foo").build()
    ).execute())).containsExactly(
      asList(foo), asList(barAndFoo), asList(fooAndBazAndQux), asList(barAndFooAndBazAndQux)
    );

    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("foo and bar").build()
    ).execute())).containsExactly(
      asList(barAndFoo), asList(barAndFooAndBazAndQux)
    );

    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("frontend")
        .parseAnnotationQuery("foo and bar and baz=qux")
        .build()
    ).execute())).containsExactly(
      asList(barAndFooAndBazAndQux)
    );

    // ensure we can search only by tag key
    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("baz").build()
    ).execute())).containsExactly(
      asList(fooAndBazAndQux), asList(barAndFooAndBazAndQux)
    );
  }

  /** This test makes sure that annotation queries pay attention to which host recorded data */
  @Test public void getTraces_differentiateOnServiceName() throws IOException {
    Span trace1 = Span.newBuilder().traceId("1").name("1").id(1)
      .kind(Span.Kind.CLIENT)
      .timestamp((TODAY + 1) * 1000L)
      .duration(3000L)
      .localEndpoint(FRONTEND)
      .addAnnotation(((TODAY + 1) * 1000L) + 500, "web")
      .putTag("local", "web")
      .putTag("web-b", "web")
      .build();

    Span trace1Server = Span.newBuilder().traceId("1").name("1").id(1)
      .kind(Span.Kind.SERVER)
      .shared(true)
      .localEndpoint(BACKEND)
      .timestamp((TODAY + 2) * 1000L)
      .duration(1000L)
      .build();

    Span trace2 = Span.newBuilder().traceId("2").name("2").id(2)
      .timestamp((TODAY + 11) * 1000L)
      .duration(3000L)
      .kind(Span.Kind.CLIENT)
      .localEndpoint(BACKEND)
      .addAnnotation(((TODAY + 11) * 1000) + 500, "app")
      .putTag("local", "app")
      .putTag("app-b", "app")
      .build();

    Span trace2Server = Span.newBuilder().traceId("2").name("2").id(2)
      .shared(true)
      .kind(Span.Kind.SERVER)
      .localEndpoint(FRONTEND)
      .timestamp((TODAY + 12) * 1000L)
      .duration(1000L).build();

    accept(trace1, trace1Server, trace2, trace2Server);

    // Sanity check
    assertThat(store().getTrace(trace1.traceId()).execute())
      .containsExactlyInAnyOrder(trace1, trace1Server);
    assertThat(sortTrace(store().getTrace(trace2.traceId()).execute()))
      .containsExactly(trace2, trace2Server);
    assertThat(sortTraces(store().getTraces(requestBuilder().build()).execute()))
      .containsExactly(asList(trace1, trace1Server), asList(trace2, trace2Server));

    // We only return traces where the service specified caused the data queried.
    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("web").build()
    ).execute())).containsExactly(asList(trace1, trace1Server));

    assertThat(store().getTraces(
      requestBuilder().serviceName("backend").parseAnnotationQuery("web").build()
    ).execute()).isEmpty();

    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("backend").parseAnnotationQuery("app").build()
    ).execute())).containsExactly(asList(trace2, trace2Server));

    assertThat(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("app").build()
    ).execute()).isEmpty();

    // tags are returned on annotation queries
    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("web-b").build()
    ).execute())).containsExactly(asList(trace1, trace1Server));

    assertThat(store().getTraces(
      requestBuilder().serviceName("backend").parseAnnotationQuery("web-b").build()
    ).execute()).isEmpty();

    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("backend").parseAnnotationQuery("app-b").build()
    ).execute())).containsExactly(asList(trace2, trace2Server));

    assertThat(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("app-b").build()
    ).execute()).isEmpty();

    // We only return traces where the service specified caused the tag queried.
    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("local=web").build()
    ).execute())).containsExactly(asList(trace1, trace1Server));

    assertThat(store().getTraces(
      requestBuilder().serviceName("backend").parseAnnotationQuery("local=web").build()
    ).execute()).isEmpty();

    assertThat(sortTraces(store().getTraces(
      requestBuilder().serviceName("backend").parseAnnotationQuery("local=app").build()
    ).execute())).containsExactly(asList(trace2, trace2Server));

    assertThat(store().getTraces(
      requestBuilder().serviceName("frontend").parseAnnotationQuery("local=app").build()
    ).execute()).isEmpty();
  }

  /** limit should apply to traces closest to endTs */
  @Test public void getTraces_limit() throws IOException {
    Span span1 = Span.newBuilder()
      .traceId("a")
      .id("1")
      .timestamp((TODAY + 1) * 1000L)
      .localEndpoint(FRONTEND)
      .build();
    Span span2 = span1.toBuilder().traceId("b").timestamp((TODAY + 2) * 1000L).build();
    accept(span1, span2);

    assertThat(
      store().getTraces(requestBuilder().serviceName("frontend").limit(1).build()).execute())
      .extracting(t -> t.get(0).id())
      .containsExactly(span2.id());
  }

  /** Traces whose root span has timestamps between (endTs - lookback) and endTs are returned */
  @Test public void getTraces_endTsAndLookback() throws IOException {
    Span span1 = Span.newBuilder()
      .traceId("a")
      .id("1")
      .timestamp((TODAY + 1) * 1000L)
      .localEndpoint(FRONTEND)
      .build();
    Span span2 = span1.toBuilder().traceId("b").timestamp((TODAY + 2) * 1000L).build();
    accept(span1, span2);

    assertThat(store().getTraces(requestBuilder().endTs(TODAY).build()).execute())
      .isEmpty();
    assertThat(store().getTraces(requestBuilder().endTs(TODAY + 1).build()).execute())
      .extracting(t -> t.get(0).id())
      .containsExactly(span1.id());
    assertThat(store().getTraces(requestBuilder().endTs(TODAY + 2).build()).execute())
      .extracting(t -> t.get(0).id())
      .containsExactlyInAnyOrder(span1.id(), span2.id());
    assertThat(store().getTraces(requestBuilder().endTs(TODAY + 3).build()).execute())
      .extracting(t -> t.get(0).id())
      .containsExactlyInAnyOrder(span1.id(), span2.id());

    assertThat(store().getTraces(requestBuilder().endTs(TODAY).build()).execute())
      .isEmpty();
    assertThat(store().getTraces(requestBuilder().endTs(TODAY + 1).lookback(1).build()).execute())
      .extracting(t -> t.get(0).id())
      .containsExactly(span1.id());
    assertThat(store().getTraces(requestBuilder().endTs(TODAY + 2).lookback(1).build()).execute())
      .extracting(t -> t.get(0).id())
      .containsExactlyInAnyOrder(span1.id(), span2.id());
    assertThat(store().getTraces(requestBuilder().endTs(TODAY + 3).lookback(1).build()).execute())
      .extracting(t -> t.get(0).id())
      .containsExactlyInAnyOrder(span2.id());
  }

  // Bugs have happened in the past where trace limit was mistaken for span count.
  @Test public void traceWithManySpans() throws IOException {
    Span[] trace = new Span[101];
    trace[0] = Span.newBuilder().traceId("f66529c8cc356aa0").id("93288b4644570496").name("get")
      .timestamp(TODAY * 1000).duration(350 * 1000L)
      .kind(Span.Kind.SERVER)
      .localEndpoint(BACKEND)
      .build();

    IntStream.range(1, trace.length).forEach(i ->
      trace[i] = Span.newBuilder().traceId(trace[0].traceId()).parentId(trace[0].id()).id(i)
        .name("foo")
        .timestamp((TODAY + i) * 1000).duration(10L)
        .localEndpoint(BACKEND)
        .build());

    accept(trace);

    assertThat(store().getTraces(requestBuilder().build()).execute())
      .flatExtracting(t -> t)
      .containsExactlyInAnyOrder(trace);
    assertThat(store().getTrace(trace[0].traceId()).execute())
      .containsExactlyInAnyOrder(trace);
  }

  @Test public void getServiceNames_includesLocalServiceName() throws Exception {
    assertThat(store().getServiceNames().execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(store().getServiceNames().execute())
      .contains("frontend");
  }

  @Test public void getSpanNames() throws Exception {
    assertThat(store().getSpanNames("frontend").execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(store().getSpanNames("frontend" + 1).execute())
      .isEmpty();

    assertThat(store().getSpanNames("frontend").execute())
      .contains(CLIENT_SPAN.name());
  }

  @Test public void getSpanNames_allReturned() throws IOException {
    // Assure a default spanstore limit isn't hit by assuming if 50 are returned, all are returned
    List<String> spanNames = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      String suffix = i < 10 ? "0" + i : String.valueOf(i);
      accept(CLIENT_SPAN.toBuilder().id(i + 1).name("yak" + suffix).build());
      spanNames.add("yak" + suffix);
    }

    assertThat(store().getSpanNames("frontend").execute())
      .containsExactlyInAnyOrderElementsOf(spanNames);
  }

  @Test public void getAllServiceNames_noServiceName() throws IOException {
    accept(Span.newBuilder().traceId("a").id("a").build());

    assertThat(store().getServiceNames().execute()).isEmpty();
  }

  @Test public void getSpanNames_noSpanName() throws IOException {
    accept(Span.newBuilder().traceId("a").id("a").localEndpoint(FRONTEND).build());

    assertThat(store().getSpanNames("frontend").execute()).isEmpty();
  }

  @Test public void spanNamesGoLowercase() throws IOException {
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(
      requestBuilder().serviceName("frontend").spanName("GeT").build()
    ).execute()).hasSize(1);
  }

  @Test public void serviceNamesGoLowercase() throws IOException {
    accept(CLIENT_SPAN);

    assertThat(store().getSpanNames("FrOnTeNd").execute()).containsExactly("get");

    assertThat(store().getTraces(requestBuilder().serviceName("FrOnTeNd").build()).execute())
      .hasSize(1);
  }

  /** Ensure complete traces are aggregated, even if they complete after endTs */
  @Test public void getTraces_endTsInsideTheTrace() throws IOException {
    accept(TRACE);

    assertThat(sortTraces(store().getTraces(
      requestBuilder().endTs(TRACE_STARTTS + 100).lookback(200).build()
    ).execute())).containsOnly(TRACE);
  }

  protected void accept(List<Span> spans) throws IOException {
    storage().spanConsumer().accept(spans).execute();
  }

  protected void accept(Span... spans) throws IOException {
    storage().spanConsumer().accept(asList(spans)).execute();
  }

  void setupDurationData() throws IOException {
    Endpoint service1 = Endpoint.newBuilder().serviceName("service1").build();
    Endpoint service2 = Endpoint.newBuilder().serviceName("service2").build();
    Endpoint service3 = Endpoint.newBuilder().serviceName("service3").build();

    long offsetMicros = (TODAY - 3) * 1000L; // to make sure queries look back properly
    Span targz = Span.newBuilder().traceId("1").id(1L)
      .name("targz").timestamp(offsetMicros + 100L).duration(200_000L)
      .localEndpoint(service1)
      .putTag("lc", "archiver").build();
    Span tar = Span.newBuilder().traceId("1").id(2L).parentId(1L)
      .name("tar").timestamp(offsetMicros + 200L).duration(150_000L)
      .localEndpoint(service2)
      .putTag("lc", "archiver").build();
    Span gz = Span.newBuilder().traceId("1").id(3L).parentId(1L)
      .name("gz").timestamp(offsetMicros + 250L).duration(50_000L)
      .localEndpoint(service3)
      .putTag("lc", "archiver").build();
    Span zip = Span.newBuilder().traceId("3").id(3L)
      .name("zip").timestamp(offsetMicros + 130L).duration(50_000L)
      .addAnnotation(offsetMicros + 130L, "zip")
      .localEndpoint(service2)
      .putTag("lc", "archiver").build();

    List<Span> trace1 = asList(targz, tar, gz);
    List<Span> trace2 = asList(
      targz.toBuilder().traceId("2").timestamp(offsetMicros + 110L)
        .localEndpoint(service3)
        .putTag("lc", "archiver-v2").build(),
      tar.toBuilder().traceId("2").timestamp(offsetMicros + 210L)
        .localEndpoint(service2)
        .putTag("lc", "archiver").build(),
      gz.toBuilder().traceId("2").timestamp(offsetMicros + 260L)
        .localEndpoint(service1)
        .putTag("lc", "archiver").build());
    List<Span> trace3 = asList(zip);

    accept(trace1.toArray(new Span[0]));
    accept(trace2.toArray(new Span[0]));
    accept(trace3.toArray(new Span[0]));
  }

  protected static QueryRequest.Builder requestBuilder() {
    return QueryRequest.newBuilder().endTs(TODAY + DAY).lookback(DAY * 2).limit(100);
  }

  static List<List<Span>> sortTraces(List<List<Span>> traces) {
    List<List<Span>> result = new ArrayList<>();
    for (List<Span> trace : traces) result.add(sortTrace(trace));
    Collections.sort(result, Comparator.comparing(o -> o.get(0).traceId()));
    return result;
  }

  static ArrayList<Span> sortTrace(List<Span> trace) {
    ArrayList<Span> result = new ArrayList<>(trace);
    Collections.sort(result, Comparator.comparing(Span::timestampAsLong));
    return result;
  }
}
