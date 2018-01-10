/**
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
package zipkin.storage.cassandra;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.Util;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStoreTest;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

abstract class CassandraSpanStoreTest extends SpanStoreTest {

  private final CassandraStorage storage;

  CassandraSpanStoreTest() {
    storage = storageBuilder().build();
  }

  abstract CassandraStorage.Builder storageBuilder();

  @Override protected final CassandraStorage storage() {
    return storage;
  }

  @Override public final void clear() {
    storage.clear();
  }

  /** Cassandra indexing is performed separately, allowing the raw span to be stored unaltered. */
  @Test
  public void rawTraceStoredWithoutAdjustments() {
    Span rawSpan = TestObjects.TRACE.get(0).toBuilder().timestamp(null).duration(null).build();
    accept(rawSpan);

    // At query time, timestamp and duration are added.
    assertThat(store().getTrace(rawSpan.traceIdHigh, rawSpan.traceId))
        .containsExactly(ApplyTimestampAndDuration.apply(rawSpan));

    // Unlike other stores, Cassandra can show that timestamp and duration weren't reported
    assertThat(store().getRawTrace(rawSpan.traceIdHigh, rawSpan.traceId))
        .containsExactly(rawSpan);
  }

  @Test
  public void overFetchesToCompensateForDuplicateIndexData() {
    int traceCount = 100;

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

    accept(spans.toArray(new Span[0]));

    // Index ends up containing more rows than services * trace count, and cannot be de-duped
    // in a server-side query.
    assertThat(rowCount(Tables.SERVICE_NAME_INDEX))
        .isGreaterThan(traceCount * store().getServiceNames().size());

    // Implementation over-fetches on the index to allow the user to receive unsurprising results.
    assertThat(store().getTraces(QueryRequest.builder().limit(traceCount).build()))
        .hasSize(traceCount);
  }

  @Test
  public void searchingByAnnotationShouldFilterBeforeLimiting() {
    long now = System.currentTimeMillis();

    int queryLimit = 2;
    Endpoint endpoint = TestObjects.LOTS_OF_SPANS[0].annotations.get(0).endpoint;
    BinaryAnnotation ba = BinaryAnnotation.create("host.name", "host1", endpoint);

    int nbTraceFetched = queryLimit * storage().indexFetchMultiplier;
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

  long rowCount(String table) {
    return storage().session().execute("SELECT COUNT(*) from " + table).one().getLong(0);
  }

  @Override public void getTraces_duration() {
    try {
      super.getTraces_duration();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage())
        .isEqualTo("getTraces with duration is unsupported. Upgrade to the new cassandra3 schema.");
      throw new AssumptionViolatedException("Upgrade to cassandra3 if you want duration queries");
    }
  }

  @Override public void getTraces_duration_allServices() {
    try {
      super.getTraces_duration_allServices();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      throw new AssumptionViolatedException("Upgrade to cassandra3 to search all services");
    }
  }

  @Override public void getTraces_exactMatch_allServices() {
    try {
      super.getTraces_exactMatch_allServices();
      failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    } catch (IllegalArgumentException e) {
      throw new AssumptionViolatedException("Upgrade to cassandra3 to search all services");
    }
  }

  @Override protected void accept(Span... spans) {
    // TODO: this avoids overrunning the cluster with BusyPoolException
    for (List<Span> nextChunk : Lists.partition(asList(spans), 100)) {
      super.accept(nextChunk.toArray(new Span[0]));
    }
  }
}
