/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import zipkin2.Span;
import zipkin2.storage.StorageComponent.Builder;

import static zipkin2.storage.cassandra.InternalForTests.writeDependencyLinks;
import static zipkin2.storage.cassandra.Schema.TABLE_AUTOCOMPLETE_TAGS;
import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_REMOTE_SERVICES;
import static zipkin2.storage.cassandra.Schema.TABLE_SERVICE_SPANS;
import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE;
import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_SPAN;

@Testcontainers
@Tag("docker")
class ITCassandraStorage {
  static final List<String> SEARCH_TABLES = List.of(
    TABLE_AUTOCOMPLETE_TAGS,
    TABLE_SERVICE_REMOTE_SERVICES,
    TABLE_SERVICE_SPANS,
    TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE,
    TABLE_TRACE_BY_SERVICE_SPAN
  );

  @Container static CassandraContainer cassandra = new CassandraContainer();

  @Nested
  class ITTraces extends zipkin2.storage.ITTraces<CassandraStorage> {
    @Override protected Builder newStorageBuilder(TestInfo testInfo) {
      return cassandra.newStorageBuilder();
    }

    @Override
    @Test
    @Disabled("No consumer-side span deduplication")
    public void getTrace_deduplicates(TestInfo testInfo) {
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      cassandra.clear(storage);
    }
  }

  @Nested
  class ITSpanStore extends zipkin2.storage.ITSpanStore<CassandraStorage> {
    @Override protected Builder newStorageBuilder(TestInfo testInfo) {
      return cassandra.newStorageBuilder();
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      cassandra.clear(storage);
    }
  }

  @Nested
  class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse<CassandraStorage> {
    @Override protected Builder newStorageBuilder(TestInfo testInfo) {
      return cassandra.newStorageBuilder();
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      cassandra.clear(storage);
    }
  }

  @Nested
  class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse<CassandraStorage> {
    CassandraStorage strictTraceId;

    @Override protected Builder newStorageBuilder(TestInfo testInfo) {
      return cassandra.newStorageBuilder();
    }

    @BeforeEach void initializeStorageBeforeSwitch() {
      strictTraceId = CassandraContainer.newStorageBuilder(storage.contactPoints)
        .keyspace(storage.keyspace)
        .build();
    }

    @AfterEach void closeStorageBeforeSwitch() {
      if (strictTraceId != null) {
        strictTraceId.close();
        strictTraceId = null;
      }
    }

    /** Ensures we can still lookup fully 128-bit traces when strict trace ID disabled */
    @Test
    public void getTraces_128BitTraceId(TestInfo testInfo) throws Exception {
      getTraces_128BitTraceId(accept128BitTrace(strictTraceId, testInfo), testInfo);
    }

    /** Ensures data written before strict trace ID was enabled can be read */
    @Test void getTrace_retrievesBy128BitTraceId_afterSwitch(TestInfo testInfo) throws Exception {
      List<Span> trace = accept128BitTrace(strictTraceId, testInfo);

      assertGetTraceReturns(trace.get(0).traceId(), trace);
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      cassandra.clear(storage);
    }
  }

  @Nested
  class ITServiceAndSpanNames extends zipkin2.storage.ITServiceAndSpanNames<CassandraStorage> {
    @Override protected Builder newStorageBuilder(TestInfo testInfo) {
      return cassandra.newStorageBuilder();
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      cassandra.clear(storage);
    }
  }

  @Nested
  class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags<CassandraStorage> {
    @Override protected Builder newStorageBuilder(TestInfo testInfo) {
      return cassandra.newStorageBuilder();
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      cassandra.clear(storage);
    }
  }

  @Nested
  class ITDependencies extends zipkin2.storage.ITDependencies<CassandraStorage> {
    @Override protected Builder newStorageBuilder(TestInfo testInfo) {
      return cassandra.newStorageBuilder();
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      cassandra.clear(storage);
    }

    /**
     * The current implementation does not include dependency aggregation. It includes retrieval of
     * pre-aggregated links, usually made via zipkin-dependencies
     */
    @Override protected void processDependencies(List<Span> spans) {
      aggregateLinks(spans).forEach(
        (midnight, links) -> writeDependencyLinks(storage, links, midnight));
      blockWhileInFlight();
    }
  }

  @Nested
  class ITSpanConsumer extends zipkin2.storage.cassandra.ITSpanConsumer {
    @Override protected Builder newStorageBuilder(TestInfo testInfo) {
      return cassandra.newStorageBuilder();
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      cassandra.clear(storage);
    }
  }
}
