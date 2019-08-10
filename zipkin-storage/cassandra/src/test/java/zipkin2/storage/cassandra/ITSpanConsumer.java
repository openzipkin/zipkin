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
package zipkin2.storage.cassandra;

import java.io.IOException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.storage.ITStorage;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.StorageComponent;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.FRONTEND;

abstract class ITSpanConsumer extends ITStorage<CassandraStorage> {

  @Override protected boolean initializeStoragePerTest() {
    return true;
  }

  @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
    return storageBuilder().keyspace(InternalForTests.keyspace(testInfo));
  }

  @Override protected void configureStorageForTest(StorageComponent.Builder storage) {
    storage.autocompleteKeys(asList("environment"));
  }

  @Override public void clear() {
    // Just let the data pile up to prevent warnings and slowness.
  }

  abstract CassandraStorage.Builder storageBuilder();

  /**
   * {@link Span#duration()} == 0 is likely to be a mistake, and coerces to null. It is not helpful
   * to index rows who have no duration.
   */
  @Test public void doesntIndexSpansMissingDuration() throws IOException {
    Span span = Span.newBuilder().traceId("1").id("1").name("get").duration(0L).build();

    accept(spanConsumer(), span);

    assertThat(rowCountForTraceByServiceSpan(storage())).isZero();
  }

  /**
   * Simulates a trace with a step pattern, where each span starts a millisecond after the prior
   * one. The consumer code optimizes index inserts to only represent the interval represented by
   * the trace as opposed to each individual timestamp.
   */
  @Test public void skipsRedundantIndexingInATrace() throws IOException {
    Span[] trace = new Span[101];
    trace[0] = TestObjects.CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).build();

    IntStream.range(0, 100).forEach(i -> trace[i + 1] = Span.newBuilder()
      .traceId(trace[0].traceId())
      .parentId(trace[0].id())
      .id(Long.toHexString(i))
      .name("get")
      .kind(Span.Kind.CLIENT)
      .localEndpoint(FRONTEND)
      .timestamp(
        trace[0].timestamp() + i * 1000) // all peer span timestamps happen a millisecond later
      .duration(10L)
      .build());

    accept(spanConsumer(), trace);
    assertThat(rowCountForTraceByServiceSpan(storage()))
      .isGreaterThanOrEqualTo(4L);
    assertThat(rowCountForTraceByServiceSpan(storage()))
      .isGreaterThanOrEqualTo(4L);

    // sanity check base case
    accept(storage().toBuilder().strictTraceId(false).build().spanConsumer(), trace);

    assertThat(rowCountForTraceByServiceSpan(storage()))
      .isGreaterThanOrEqualTo(120L); // TODO: magic number
    assertThat(rowCountForTraceByServiceSpan(storage()))
      .isGreaterThanOrEqualTo(120L);
  }

  @Test
  public void insertTags_SelectTags_CalculateCount() throws IOException {
    Span[] trace = new Span[101];
    trace[0] = TestObjects.CLIENT_SPAN.toBuilder().kind(Span.Kind.SERVER).build();

    IntStream.range(0, 100).forEach(i -> trace[i + 1] = Span.newBuilder()
      .traceId(trace[0].traceId())
      .parentId(trace[0].id())
      .id(Long.toHexString(i))
      .name("get")
      .kind(Span.Kind.CLIENT)
      .localEndpoint(FRONTEND)
      .putTag("environment", "dev")
      .putTag("a", "b")
      .timestamp(
        trace[0].timestamp() + i * 1000) // all peer span timestamps happen a millisecond later
      .duration(10L)
      .build());

    accept(spanConsumer(), trace);

    assertThat(rowCountForTags(storage()))
      .isEqualTo(1L); // Since tag {a,b} are not in the whitelist

    assertThat(getTagValue(storage(), "environment")).isEqualTo("dev");
  }

  void accept(SpanConsumer consumer, Span... spans) throws IOException {
    consumer.accept(asList(spans)).execute();
  }

  static long rowCountForTraceByServiceSpan(CassandraStorage storage) {
    return storage
      .session()
      .execute("SELECT COUNT(*) from " + Schema.TABLE_TRACE_BY_SERVICE_SPAN)
      .one()
      .getLong(0);
  }

  static long rowCountForTags(CassandraStorage storage) {
    return storage
      .session()
      .execute("SELECT COUNT(*) from " + Schema.TABLE_AUTOCOMPLETE_TAGS)
      .one()
      .getLong(0);
  }

  static String getTagValue(CassandraStorage storage, String key) {
    return storage
      .session()
      .execute("SELECT value from " + Schema.TABLE_AUTOCOMPLETE_TAGS + " WHERE key='environment'")
      .one()
      .getString(0);
  }
}
