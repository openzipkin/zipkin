/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.appendSuffix;
import static zipkin2.TestObjects.newTrace;
import static zipkin2.storage.ITDependencies.aggregateLinks;
import static zipkin2.storage.cassandra.InternalForTests.writeDependencyLinks;

/**
 * Large amounts of writes can make other tests flake. This can happen for reasons such as
 * overloading the test Cassandra container or knock-on effects of tombstones left from {@link
 * CassandraContainer#clear(CassandraStorage)}.
 *
 * <p>Tests here share a different Cassandra container and each method runs in an isolated
 * keyspace. As schema installation takes ~10s, hesitate adding too many tests here.
 */
@Testcontainers
@Tag("docker")
class ITCassandraStorageHeavy {

  @Container static CassandraContainer backend = new CassandraContainer();

  @Nested
  class ITSpanStoreHeavy extends zipkin2.storage.ITSpanStoreHeavy<CassandraStorage> {
    @Override protected CassandraStorage.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.newStorageBuilder().keyspace(InternalForTests.keyspace(testInfo));
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      // Intentionally don't clean up as each method runs in an isolated keyspace. This prevents
      // adding more load to the shared Cassandra instance used for all tests.
    }

    @Test void overFetchesToCompensateForDuplicateIndexData(TestInfo testInfo) throws Exception {
      String testSuffix = testSuffix(testInfo);
      int traceCount = 2000;

      List<Span> spans = new ArrayList<>();
      for (int i = 0; i < traceCount; i++) {
        final long delta = i * 1000; // all timestamps happen a millisecond later
        for (Span s : newTrace(testSuffix)) {
          Span.Builder builder = s.toBuilder()
            .timestamp(s.timestampAsLong() + delta)
            .clearAnnotations();
          s.annotations().forEach(a -> builder.addAnnotation(a.timestamp() + delta, a.value()));
          spans.add(builder.build());
        }
      }

      accept(spans.toArray(new Span[0]));

      // Index ends up containing more rows than services * trace count, and cannot be de-duped
      // in a server-side query.
      int localServiceCount = storage.serviceAndSpanNames().getServiceNames().execute().size();
      assertThat(storage
        .session()
        .execute("SELECT COUNT(*) from trace_by_service_span")
        .one()
        .getLong(0))
        .isGreaterThan((long) traceCount * localServiceCount);

      // Implementation over-fetches on the index to allow the user to receive unsurprising results.
      QueryRequest request = requestBuilder()
        // Ensure we use serviceName so that trace_by_service_span is used
        .serviceName(appendSuffix(FRONTEND.serviceName(), testSuffix))
        .lookback(DAY).limit(traceCount).build();
      assertThat(store().getTraces(request).execute())
        .hasSize(traceCount);
    }
  }

  @Nested
  class ITDependenciesHeavy extends zipkin2.storage.ITDependenciesHeavy<CassandraStorage> {
    @Override protected CassandraStorage.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.newStorageBuilder().keyspace(InternalForTests.keyspace(testInfo));
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      // Intentionally don't clean up as each method runs in an isolated keyspace. This prevents
      // adding more load to the shared Cassandra instance used for all tests.
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
  class ITEnsureSchema extends zipkin2.storage.cassandra.ITEnsureSchema {
    @Override protected CassandraStorage.Builder newStorageBuilder(TestInfo testInfo) {
      return backend.newStorageBuilder().keyspace(InternalForTests.keyspace(testInfo));
    }

    @Override CqlSession session() {
      return backend.globalSession;
    }

    @Override protected void blockWhileInFlight() {
      CassandraContainer.blockWhileInFlight(storage);
    }

    @Override public void clear() {
      // Intentionally don't clean up as each method runs in an isolated keyspace. This prevents
      // adding more load to the shared Cassandra instance used for all tests.
    }
  }
}
