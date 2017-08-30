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
package zipkin.internal.v2.storage;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.internal.v2.Span;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.APP_ENDPOINT;
import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.TODAY;

public class InMemoryStorageTest {
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();

  @Test public void getTraces_filteringMatchesMostRecentTraces() throws IOException {
    List<Endpoint> endpoints = IntStream.rangeClosed(1, 10).mapToObj(i ->
      Endpoint.create("service" + i, 127 << 24 | i))
      .collect(Collectors.toList());

    long gapBetweenSpans = 100;
    List<Span> earlySpans = IntStream.rangeClosed(1, 10).mapToObj(i -> Span.builder().name("early")
      .traceId(i).id(i).timestamp((TODAY - i) * 1000).duration(1L)
      .localEndpoint(endpoints.get(i - 1)).build()).collect(toList());

    List<Span> lateSpans = IntStream.rangeClosed(1, 10).mapToObj(i -> Span.builder().name("late")
      .traceId(i + 10).id(i + 10).timestamp((TODAY + gapBetweenSpans - i) * 1000).duration(1L)
      .localEndpoint(endpoints.get(i - 1)).build()).collect(toList());

    storage.accept(earlySpans).execute();
    storage.accept(lateSpans).execute();

    List<Span>[] earlyTraces =
      earlySpans.stream().map(Collections::singletonList).toArray(List[]::new);
    List<Span>[] lateTraces =
      lateSpans.stream().map(Collections::singletonList).toArray(List[]::new);

    //sanity checks
    assertThat(storage.getTraces(requestBuilder().serviceName("service1").build()).execute())
      .containsExactly(lateTraces[0], earlyTraces[0]);

    assertThat(storage.getTraces(requestBuilder().build()).execute())
      .hasSize(20);

    assertThat(storage.getTraces(requestBuilder()
      .limit(10).build()).execute())
      .containsExactly(lateTraces);

    assertThat(storage.getTraces(requestBuilder()
      .endTs(TODAY + gapBetweenSpans).lookback(gapBetweenSpans).build()).execute())
      .containsExactly(lateTraces);

    assertThat(storage.getTraces(requestBuilder()
      .endTs(TODAY).build()).execute())
      .containsExactly(earlyTraces);
  }

  /** It should be safe to run dependency link jobs twice */
  @Test public void replayOverwrites() throws IOException {
    Span span = Span.builder().traceId(10L).id(10L).name("receive")
      .kind(Span.Kind.CONSUMER)
      .localEndpoint(APP_ENDPOINT)
      .remoteEndpoint(Endpoint.builder().serviceName("kafka").build())
      .timestamp(TODAY * 1000)
      .build();

    storage.accept(asList(span));
    storage.accept(asList(span));

    assertThat(storage.getDependencies(TODAY + 1000L, TODAY).execute()).containsOnly(
      DependencyLink.builder().parent("kafka").child("app").callCount(1L).build()
    );
  }

  static QueryRequest.Builder requestBuilder() {
    return QueryRequest.newBuilder().endTs(TODAY + DAY).lookback(DAY * 2).limit(100);
  }
}
