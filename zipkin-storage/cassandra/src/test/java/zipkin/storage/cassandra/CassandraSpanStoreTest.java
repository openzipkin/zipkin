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
package zipkin.storage.cassandra;

import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Annotation;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.storage.QueryRequest;
import zipkin.storage.SpanStoreTest;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class CassandraSpanStoreTest extends SpanStoreTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private final CassandraStorage storage;

  public CassandraSpanStoreTest() {
    this.storage = CassandraTestGraph.INSTANCE.storage.get();
  }

  CassandraSpanStoreTest(CassandraStorage storage) {
    this.storage = storage;
  }

  @Override protected CassandraStorage storage() {
    return storage;
  }

  @Override public void clear() {
    storage.clear();
  }

  /** Cassandra indexing is performed separately, allowing the raw span to be stored unaltered. */
  @Test
  public void rawTraceStoredWithoutAdjustments() {
    Span rawSpan = TestObjects.TRACE.get(0).toBuilder().timestamp(null).duration(null).build();
    accept(rawSpan);

    // At query time, timestamp and duration are added.
    assertThat(store().getTrace(rawSpan.traceId))
        .containsExactly(ApplyTimestampAndDuration.apply(rawSpan));

    // Unlike other stores, Cassandra can show that timestamp and duration weren't reported
    assertThat(store().getRawTrace(rawSpan.traceId))
        .containsExactly(rawSpan);
  }

  /**
   * The PRIMARY KEY of {@link Tables#SERVICE_NAME_INDEX} doesn't consider trace_id, so will only
   * see bucket count traces to a service per millisecond.
   */
  @Override public void getTraces_manyTraces() {
    thrown.expect(AssertionError.class);
    thrown.expectMessage("Expected size:<1000> but was:<10>");

    super.getTraces_manyTraces();
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

  long rowCount(String table) {
    return storage.session().execute("SELECT COUNT(*) from " + table).one().getLong(0);
  }
}
