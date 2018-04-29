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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Constants;
import zipkin.Span;
import zipkin.TestObjects;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

abstract class CassandraSpanConsumerTest {

  abstract protected CassandraStorage storage();

  @Before
  public void clear() {
    storage().clear();
  }

  /**
   * Core/Boundary annotations like "sr" aren't queryable, and don't add value to users. Address
   * annotations, like "sa", don't have string values, so are similarly not queryable. Skipping
   * indexing of such annotations dramatically reduces the load on cassandra and size of indexes.
   */
  @Test
  public void doesntIndexCoreOrNonStringAnnotations() {
    Span span = TestObjects.TRACE.get(1);

    assertThat(span.annotations)
        .extracting(a -> a.value)
        .matches(Constants.CORE_ANNOTATIONS::containsAll);

    assertThat(span.binaryAnnotations)
        .extracting(b -> b.key)
        .containsOnly(Constants.SERVER_ADDR, Constants.CLIENT_ADDR);

    accept(span);

    assertThat(rowCount(Tables.ANNOTATIONS_INDEX)).isZero();
  }

  /**
   * Simulates a trace with a step pattern, where each span starts a millisecond after the prior
   * one. The consumer code optimizes index inserts to only represent the interval represented by
   * the trace as opposed to each individual timestamp.
   */
  @Test
  public void skipsRedundantIndexingInATrace() {
    Span[] trace = new Span[101];
    trace[0] = TestObjects.TRACE.get(0);

    IntStream.range(0, 100).forEach(i -> {
      Span s = TestObjects.TRACE.get(1);
      trace[i + 1] = s.toBuilder()
          .id(s.id + i)
          .timestamp(s.timestamp + i * 1000) // all peer span timestamps happen a millisecond later
          .annotations(s.annotations.stream()
              .map(a -> Annotation.create(a.timestamp + i * 1000, a.value, a.endpoint))
              .collect(toList()))
          .build();
    });

    accept(trace);
    assertThat(rowCount(Tables.SERVICE_SPAN_NAME_INDEX)).isEqualTo(4L);
    assertThat(rowCount(Tables.SERVICE_NAME_INDEX)).isEqualTo(4L);

    // sanity check base case
    clear();

    CassandraSpanConsumer withoutOptimization = new CassandraSpanConsumer(
        storage().session(),
        storage().bucketCount,
        storage().spanTtl,
        storage().indexTtl,
        null /** Disables optimization, just like CassandraStorage.indexCacheMax = 0 would */
    );
    Futures.getUnchecked(withoutOptimization.accept(ImmutableList.copyOf(trace)));
    assertThat(rowCount(Tables.SERVICE_SPAN_NAME_INDEX)).isEqualTo(201L);
    assertThat(rowCount(Tables.SERVICE_NAME_INDEX)).isEqualTo(201L);
  }

  void accept(Span... spans) {
    Futures.getUnchecked(storage().computeGuavaSpanConsumer().accept(ImmutableList.copyOf(spans)));
  }

  long rowCount(String table) {
    return storage().session().execute("SELECT COUNT(*) from " + table).one().getLong(0);
  }
}
