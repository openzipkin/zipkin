/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.TODAY;

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

  @Test public void getTraces_filteringMatchesMostRecentTraces() throws Exception {
    List<Endpoint> endpoints = IntStream.rangeClosed(1, 10)
      .mapToObj(i -> Endpoint.newBuilder().serviceName("service" + i).ip("127.0.0.1").build())
      .collect(Collectors.toList());

    long gapBetweenSpans = 100;
    Span[] earlySpans =
      IntStream.rangeClosed(1, 10).mapToObj(i -> Span.newBuilder().name("early")
        .traceId(Integer.toHexString(i)).id(Integer.toHexString(i))
        .timestamp((TODAY - i) * 1000).duration(1L)
        .localEndpoint(endpoints.get(i - 1)).build()).toArray(Span[]::new);

    Span[] lateSpans = IntStream.rangeClosed(1, 10).mapToObj(i -> Span.newBuilder().name("late")
      .traceId(Integer.toHexString(i + 10)).id(Integer.toHexString(i + 10))
      .timestamp((TODAY + gapBetweenSpans - i) * 1000).duration(1L)
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
      .serviceName(CLIENT_SPAN.localServiceName() + 1)
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .serviceName(CLIENT_SPAN.localServiceName())
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
      .minDuration(CLIENT_SPAN.duration() + 1)
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.duration())
      .build()).execute()).flatExtracting(l -> l).contains(CLIENT_SPAN);
  }

  @Test public void getTraces_maxDuration() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.duration() - 2)
      .maxDuration(CLIENT_SPAN.duration() - 1)
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.duration())
      .maxDuration(CLIENT_SPAN.duration())
      .build()).execute()).flatExtracting(l -> l).contains(CLIENT_SPAN);
  }

  @Test public void getSpanNames() throws Exception {
    assertThat(store().getSpanNames(CLIENT_SPAN.localServiceName()).execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(store().getSpanNames(CLIENT_SPAN.localServiceName() + 1).execute())
      .isEmpty();

    assertThat(store().getSpanNames(CLIENT_SPAN.localServiceName()).execute())
      .contains(CLIENT_SPAN.name());
  }

  @Test public void getServiceNames_includesLocalServiceName() throws Exception {
    assertThat(store().getServiceNames().execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(store().getServiceNames().execute())
      .contains(CLIENT_SPAN.localServiceName());
  }

  Span with128BitId1 = Span.newBuilder()
    .traceId("baaaaaaaaaaaaaaaa").id("a").timestamp(TODAY * 1000).build();
  Span with64BitId1 = Span.newBuilder()
    .traceId("aaaaaaaaaaaaaaaa").id("b").timestamp((TODAY + 1) * 1000).build();
  Span with128BitId2 = Span.newBuilder()
    .traceId("21111111111111111").id("1").timestamp(TODAY * 1000).build();
  Span with64BitId2 = Span.newBuilder()
    .traceId("1111111111111111").id("2").timestamp((TODAY + 1) * 1000).build();
  Span with128BitId3 = Span.newBuilder()
    .traceId("effffffffffffffff").id("1").timestamp(TODAY * 1000).build();
  Span with64BitId3 = Span.newBuilder()
    .traceId("ffffffffffffffff").id("2").timestamp(TODAY * 1000).build();

  /** current implementation cannot return exact form reported */
  @Test
  public void getTraces_retrievesBy64Or128BitTraceId() throws Exception {
    accept(with128BitId1, with64BitId1, with128BitId2, with64BitId2, with128BitId3, with64BitId3);

    List<List<Span>> resultsWithBothIdLength = sortTraces(store()
      .getTraces(asList(
        with128BitId1.traceId(),
        with64BitId1.traceId(),
        with128BitId3.traceId(),
        with64BitId3.traceId()
      )).execute());

    assertThat(resultsWithBothIdLength)
      .containsExactly(
        asList(with128BitId1),
        asList(with128BitId3),
        asList(with64BitId1),
        asList(with64BitId3)
      );

    List<List<Span>> resultsWith64BitIdLength = sortTraces(store()
      .getTraces(asList(
        with64BitId1.traceId(), with64BitId3.traceId()
      )).execute());

    assertThat(resultsWith64BitIdLength)
      .containsExactly(asList(with64BitId1), asList(with64BitId3));

    List<List<Span>> resultsWith128BitIdLength = sortTraces(store()
      .getTraces(asList(
        with128BitId1.traceId(), with128BitId3.traceId()
      )).execute());

    assertThat(resultsWith128BitIdLength)
      .containsExactly(asList(with128BitId1), asList(with128BitId3));
  }

  protected void accept(Span... spans) throws IOException {
    storage().spanConsumer().accept(asList(spans)).execute();
  }

  static QueryRequest.Builder requestBuilder() {
    return QueryRequest.newBuilder().endTs(TODAY + DAY).lookback(DAY * 2).limit(100);
  }

  static List<List<Span>> sortTraces(List<List<Span>> traces) {
    List<List<Span>> result = new ArrayList<>();
    for (List<Span> trace: traces) {
      ArrayList<Span> sorted = new ArrayList<>(trace);
      Collections.sort(sorted, Comparator.comparing(Span::traceId));
      result.add(sorted);
    }
    Collections.sort(result, Comparator.comparing(o -> o.get(0).traceId()));
    return result;
  }
}
