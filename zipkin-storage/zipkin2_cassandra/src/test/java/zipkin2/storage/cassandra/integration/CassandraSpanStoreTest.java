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
package zipkin2.storage.cassandra.integration;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.Session;
import com.google.common.util.concurrent.Uninterruptibles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.Util;
import zipkin.internal.V2StorageComponent;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStoreTest;
import zipkin.storage.StorageComponent;
import zipkin2.storage.cassandra.CassandraStorage;
import zipkin2.storage.cassandra.InternalForTests;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

abstract class CassandraSpanStoreTest extends SpanStoreTest {

  protected abstract CassandraStorage v2Storage();

  @Override protected final StorageComponent storage() {
    return V2StorageComponent.create(v2Storage());
  }

  @Test
  public void overFetchesToCompensateForDuplicateIndexData() {
    int traceCount = 2000;

    List<Span> spans = new ArrayList<>();
    for (int i = 0; i < traceCount; i++) {
      final long delta = i * 1000; // all timestamps happen a millisecond later
      for (Span s : TestObjects.TRACE) {
        spans.add(TestObjects.TRACE.get(0).toBuilder()
            .traceId(s.traceId + i * 10)
            .id(s.id + i * 10)
            .timestamp(s.timestamp + delta)
            .annotations(s.annotations.stream()
                .map(a -> Annotation.create(a.timestamp + delta, a.value, a.endpoint))
                .collect(toList()))
            .build());
      }
    }

    accept(spans.toArray(new Span[spans.size()]));

    // Index ends up containing more rows than services * trace count, and cannot be de-duped
    // in a server-side query.
    assertThat(InternalForTests.rowCountForTraceByServiceSpan(v2Storage()))
        .isGreaterThan(traceCount * store().getServiceNames().size());

    // Implementation over-fetches on the index to allow the user to receive unsurprising results.
    assertThat(
        store().getTraces(QueryRequest.builder().lookback(86400000L).limit(traceCount).build()))
        .hasSize(traceCount);
  }

  @Test
  public void searchingByAnnotationShouldFilterBeforeLimiting() {
    long now = System.currentTimeMillis();

    int queryLimit = 2;
    Endpoint endpoint = TestObjects.LOTS_OF_SPANS[0].annotations.get(0).endpoint;
    BinaryAnnotation ba = BinaryAnnotation.create("host.name", "host1", endpoint);

    int nbTraceFetched = queryLimit * InternalForTests.indexFetchMultiplier(v2Storage());
    IntStream.range(0, nbTraceFetched).forEach(i ->
            accept(TestObjects.LOTS_OF_SPANS[i++].toBuilder().timestamp(now - (i * 1000)).build())
    );
    // Add two traces with the binary annotation we're looking for
    IntStream.range(nbTraceFetched, nbTraceFetched + 2).forEach(i ->
            accept(TestObjects.LOTS_OF_SPANS[i++].toBuilder().timestamp(now - (i * 1000))
                    .addBinaryAnnotation(ba)
                    .build())
    );
    QueryRequest queryRequest =
            QueryRequest.builder()
                    .addBinaryAnnotation(ba.key, new String(ba.value, Util.UTF_8))
                    .serviceName(endpoint.serviceName)
                    .limit(queryLimit)
                    .build();
    assertThat(store().getTraces(queryRequest)).hasSize(queryLimit);
  }

  @Override public void getTraces_duration_allServices() {
    try {
      super.getTraces_duration_allServices();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      throw new AssumptionViolatedException("duration queries across all services is unsupported");
    }
  }

  @Override public void getTraces_exactMatch() {
    try {
      super.getTraces_exactMatch();
      failBecauseExceptionWasNotThrown(AssertionError.class);
    } catch (AssertionError e) {
      throw new AssumptionViolatedException("exact match is unsupported");
    }
  }

  @Override public void getTraces_exactMatch_allServices() {
    try {
      super.getTraces_exactMatch_allServices();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      throw new AssumptionViolatedException("duration queries across all services is unsupported");
    }
  }

  /** Makes sure the test cluster doesn't fall over on BusyPoolException */
  @Override protected void accept(Span... spans) {
    List<Span> page = new ArrayList<>();
    for (int i = 0; i < spans.length; i++) {
      page.add(spans[i]);
      if (page.size() == 10) {
        super.accept(page.toArray(new Span[0]));
        page.clear();
      }
    }
    super.accept(page.toArray(new Span[0]));

    // Now, block until writes complete, notably so we can read them.
    Session.State state = session().getState();
    refresh:
    while (true) {
      for (Host host : state.getConnectedHosts()) {
        if (state.getInFlightQueries(host) > 0) {
          Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
          state = session().getState();
          continue refresh;
        }
      }
      break;
    }
  }

  abstract Session session();
}
