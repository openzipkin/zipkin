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

package zipkin.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import zipkin.DependencyLink;
import zipkin.internal.Dependencies;
import zipkin.internal.Nullable;
import zipkin.internal.Util;
import zipkin.spanstore.guava.LazyGuavaStorageComponent;

import static java.lang.String.format;
import static zipkin.internal.Util.checkNotNull;

/**
 * CQL3 implementation of zipkin storage.
 *
 * <p>This uses zipkin-cassandra-core which packages "/cassandra-schema-cql3.txt"
 */
public final class CassandraStorage extends
    LazyGuavaStorageComponent<CassandraSpanStore, CassandraSpanConsumer> {

  public static final class Builder {
    String keyspace = "zipkin";
    String contactPoints = "localhost";
    String localDc;
    int maxConnections = 8;
    boolean ensureSchema = true;
    String username;
    String password;
    int maxTraceCols = 100000;
    int spanTtl = (int) TimeUnit.DAYS.toSeconds(7);
    int indexTtl = (int) TimeUnit.DAYS.toSeconds(3);

    /** Keyspace to store span and index data. Defaults to "zipkin" */
    public Builder keyspace(String keyspace) {
      this.keyspace = checkNotNull(keyspace, "keyspace");
      return this;
    }

    /** Comma separated list of hosts / IPs part of Cassandra cluster. Defaults to localhost */
    public Builder contactPoints(String contactPoints) {
      this.contactPoints = checkNotNull(contactPoints, "contactPoints");
      return this;
    }

    /**
     * Name of the datacenter that will be considered "local" for latency load balancing. When
     * unset, load-balancing is round-robin.
     */
    public Builder localDc(@Nullable String localDc) {
      this.localDc = localDc;
      return this;
    }

    /** Max pooled connections per datacenter-local host. Defaults to 8 */
    public Builder maxConnections(int maxConnections) {
      this.maxConnections = maxConnections;
      return this;
    }

    /**
     * Ensures that schema exists, if enabled tries to execute script io.zipkin:zipkin-cassandra-core/cassandra-schema-cql3.txt.
     * Defaults to true.
     */
    public Builder ensureSchema(boolean ensureSchema) {
      this.ensureSchema = ensureSchema;
      return this;
    }

    /** Will throw an exception on startup if authentication fails. No default. */
    public Builder username(@Nullable String username) {
      this.username = username;
      return this;
    }

    /** Will throw an exception on startup if authentication fails. No default. */
    public Builder password(@Nullable String password) {
      this.password = password;
      return this;
    }

    /**
     * Spans have multiple values for the same id. For example, a client and server contribute to
     * the same span id. When searching for spans by id, the amount of results may be larger than
     * the ids. This defines a threshold which accommodates this situation, without looking for an
     * unbounded number of results.
     */
    public Builder maxTraceCols(int maxTraceCols) {
      this.maxTraceCols = maxTraceCols;
      return this;
    }

    /** Time-to-live in seconds for span data. Defaults to 604800 (7 days) */
    public Builder spanTtl(int spanTtl) {
      this.spanTtl = spanTtl;
      return this;
    }

    /** Time-to-live in seconds for index data. Defaults to 259200 (3 days) */
    public Builder indexTtl(int indexTtl) {
      this.indexTtl = indexTtl;
      return this;
    }

    public CassandraStorage build() {
      return new CassandraStorage(this);
    }
  }

  final String keyspace;
  final int maxTraceCols;
  final int indexTtl;
  final int spanTtl;
  final ClusterProvider clusterProvider;
  final LazyRepository lazyRepository;

  CassandraStorage(Builder builder) {
    this.maxTraceCols = builder.maxTraceCols;
    this.indexTtl = builder.indexTtl;
    this.spanTtl = builder.spanTtl;
    this.keyspace = builder.keyspace;
    this.clusterProvider = new ClusterProvider(builder);
    this.lazyRepository = new LazyRepository(builder);
  }

  @Override protected CassandraSpanStore computeGuavaSpanStore() {
    return new CassandraSpanStore(lazyRepository.get(), indexTtl, maxTraceCols);
  }

  @Override protected CassandraSpanConsumer computeGuavaSpanConsumer() {
    return new CassandraSpanConsumer(lazyRepository.get(), spanTtl, indexTtl);
  }

  @Override public void close() {
    lazyRepository.close();
  }

  @VisibleForTesting void writeDependencyLinks(List<DependencyLink> links, long timestampMillis) {
    long midnight = Util.midnightUTC(timestampMillis);
    Dependencies deps = Dependencies.create(midnight, midnight /* ignored */, links);
    ByteBuffer thrift = deps.toThrift();
    Futures.getUnchecked(lazyRepository.get().storeDependencies(midnight, thrift));
  }

  @VisibleForTesting void clear() {
    try (Cluster cluster = clusterProvider.get(); Session session = cluster.connect()) {
      List<ListenableFuture<?>> futures = new LinkedList<>();
      for (String cf : ImmutableList.of(
          "traces",
          "dependencies",
          "service_names",
          "span_names",
          "service_name_index",
          "service_span_name_index",
          "annotations_index",
          "span_duration_index"
      )) {
        futures.add(session.executeAsync(format("TRUNCATE %s.%s", keyspace, cf)));
      }
      Futures.getUnchecked(Futures.allAsList(futures));
    }
  }
}
