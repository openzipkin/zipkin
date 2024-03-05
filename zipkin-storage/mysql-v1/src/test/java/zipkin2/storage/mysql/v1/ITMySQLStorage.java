/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.DependencyLink;
import zipkin2.storage.StorageComponent;

import static zipkin2.storage.ITDependencies.aggregateLinks;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("docker")
class ITMySQLStorage {

  @RegisterExtension static MySQLExtension mysql = new MySQLExtension();

  @Nested
  class ITTraces extends zipkin2.storage.ITTraces<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override @Test @Disabled("v1 format is lossy in conversion when rows as upsert")
    protected void getTrace_differentiatesDebugFromShared(TestInfo testInfo) {
    }

    @Override @Test @Disabled("v1 format is lossy in conversion when rows as upsert")
    protected void getTraces_differentiatesDebugFromShared(TestInfo testInfo) {
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override
    @Test
    @Disabled("No consumer-side span deduplication")
    public void getTrace_deduplicates(TestInfo testInfo) {
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITSpanStore extends zipkin2.storage.ITSpanStore<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override @Test @Disabled("v1 format is lossy in conversion when rows as upsert")
    protected void getTraces_differentiatesDebugFromShared(TestInfo testInfo) {
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITSpanStoreHeavy extends zipkin2.storage.ITSpanStoreHeavy<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITServiceAndSpanNames extends zipkin2.storage.ITServiceAndSpanNames<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITDependenciesOnDemand extends zipkin2.storage.ITDependencies<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITDependenciesHeavyOnDemand extends zipkin2.storage.ITDependenciesHeavy<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }
  }

  @Nested
  class ITDependenciesPreAggregated extends zipkin2.storage.ITDependencies<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }

    /**
     * The current implementation does not include dependency aggregation. It includes retrieval of
     * pre-aggregated links, usually made via zipkin-dependencies
     */
    @Override protected void processDependencies(List<zipkin2.Span> spans) throws Exception {
      aggregateDependencies(storage, spans);
    }
  }

  @Nested
  class ITDependenciesHeavyPreAggregated extends zipkin2.storage.ITDependenciesHeavy<MySQLStorage> {
    @Override protected StorageComponent.Builder newStorageBuilder(TestInfo testInfo) {
      return mysql.computeStorageBuilder();
    }

    @Override protected boolean returnsRawSpans() {
      return false;
    }

    @Override public void clear() {
      storage.clear();
    }

    /**
     * The current implementation does not include dependency aggregation. It includes retrieval of
     * pre-aggregated links, usually made via zipkin-dependencies
     */
    @Override protected void processDependencies(List<zipkin2.Span> spans) throws Exception {
      aggregateDependencies(storage, spans);
    }
  }

  static void aggregateDependencies(MySQLStorage storage, List<zipkin2.Span> spans)
    throws SQLException {
    try (Connection conn = storage.datasource.getConnection()) {
      DSLContext context = storage.context.get(conn);

      // batch insert the rows at timestamp midnight
      List<Query> inserts = new ArrayList<>();
      aggregateLinks(spans).forEach((midnight, links) -> {

        LocalDate day = Instant.ofEpochMilli(midnight)
          .atZone(ZoneId.of("UTC"))
          .toLocalDate();

        for (DependencyLink link : links) {
          inserts.add(context.insertInto(ZIPKIN_DEPENDENCIES)
            .set(ZIPKIN_DEPENDENCIES.DAY, day)
            .set(ZIPKIN_DEPENDENCIES.PARENT, link.parent())
            .set(ZIPKIN_DEPENDENCIES.CHILD, link.child())
            .set(ZIPKIN_DEPENDENCIES.CALL_COUNT, link.callCount())
            .set(ZIPKIN_DEPENDENCIES.ERROR_COUNT, link.errorCount())
            .onDuplicateKeyIgnore());
        }
      });
      context.batch(inserts).execute();
    }
  }
}
