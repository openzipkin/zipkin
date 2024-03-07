/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import zipkin2.storage.StorageComponent;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CassandraStorageBuilderTest {
  CassandraStorageBuilder<?> builder = new CassandraStorageBuilder("zipkin3") {
    @Override public StorageComponent build() {
      return null;
    }
  };

  @Test void maxConnections_setsMaxConnectionsPerDatacenterLocalHost() {
    assertThat(builder.maxConnections(16).poolingOptions().get(CONNECTION_POOL_LOCAL_SIZE))
      .isEqualTo(16);
  }

  @Test void badArguments() {
    List<Function<CassandraStorageBuilder<?>, CassandraStorageBuilder<?>>> badArguments = List.of(
      b -> b.autocompleteTtl(0),
      b -> b.autocompleteCardinality(0),
      b -> b.maxTraceCols(0),
      b -> b.indexFetchMultiplier(0)
    );
    badArguments.forEach(customizer ->
      assertThatThrownBy(() -> customizer.apply(builder))
        .isInstanceOf(IllegalArgumentException.class)
    );
  }

  /** Ensure NPE happens early. */
  @Test void nullPointers() {
    List<Function<CassandraStorageBuilder<?>, CassandraStorageBuilder<?>>> nullPointers = List.of(
      b -> b.autocompleteKeys(null),
      b -> b.contactPoints(null),
      b -> b.localDc(null),
      b -> b.keyspace(null)
    );
    nullPointers.forEach(customizer ->
      assertThatThrownBy(() -> customizer.apply(builder))
        .isInstanceOf(NullPointerException.class)
    );
  }
}
