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
package zipkin.storage.cassandra;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import zipkin.internal.Nullable;
import zipkin.storage.QueryRequest;
import zipkin.storage.StorageComponent;
import zipkin.storage.guava.LazyGuavaStorageComponent;

import static java.lang.String.format;
import static zipkin.internal.Util.checkNotNull;

/**
 * CQL3 implementation of zipkin storage.
 *
 * <p>Queries are logged to the category "com.datastax.driver.core.QueryLogger" when debug or trace
 * is enabled via SLF4J. Trace level includes bound values.
 *
 * <p>Redundant requests to store service or span names are ignored for an hour to reduce load. This
 * feature is implemented by {@link DeduplicatingExecutor}.
 *
 * <p>Schema is installed by default from "/cassandra-schema-cql3.txt"
 */
public final class CassandraStorage
    extends LazyGuavaStorageComponent<CassandraSpanStore, CassandraSpanConsumer> {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder implements StorageComponent.Builder {
    boolean strictTraceId = true;
    String keyspace = "zipkin";
    String contactPoints = "localhost";
    String localDc;
    int maxConnections = 8;
    boolean ensureSchema = true;
    boolean useSsl = false;
    String username;
    String password;
    int maxTraceCols = 100000;
    int indexCacheMax = 100000;
    int indexCacheTtl = 60;
    int indexFetchMultiplier = 3;

    /**
     * Used to avoid hot spots when writing indexes used to query by service name or annotation.
     *
     * <p>This controls the amount of buckets, or partitions writes to {@code service_name_index}
     * and {@code annotations_index}. This must be the same for all query servers, and has
     * historically always been 10.
     *
     * See https://github.com/openzipkin/zipkin/issues/623 for further explanation
     */
    int bucketCount = 10;
    int spanTtl = (int) TimeUnit.DAYS.toSeconds(7);
    int indexTtl = (int) TimeUnit.DAYS.toSeconds(3);
    SessionFactory sessionFactory = new SessionFactory.Default();

    /** {@inheritDoc} */
    @Override public Builder strictTraceId(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
      return this;
    }

    /** Override to control how sessions are created. */
    public Builder sessionFactory(SessionFactory sessionFactory) {
      this.sessionFactory = checkNotNull(sessionFactory, "sessionFactory");
      return this;
    }

    /** Keyspace to store span and index data. Defaults to "zipkin" */
    public Builder keyspace(String keyspace) {
      this.keyspace = checkNotNull(keyspace, "keyspace");
      return this;
    }

    /** Comma separated list of host addresses part of Cassandra cluster. You can also specify a custom port with 'host:port'. Defaults to localhost on port 9042 **/
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

    /**
     * Use ssl for connection.
     * Defaults to false.
     */
    public Builder useSsl(boolean useSsl) {
      this.useSsl = useSsl;
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

    /**
     * Time-to-live in seconds for span data. Defaults to 604800 (7 days)
     *
     * @deprecated current schema uses default ttls. This parameter will be removed in Zipkin 2
     */
    @Deprecated
    public Builder spanTtl(int spanTtl) {
      this.spanTtl = spanTtl;
      return this;
    }

    /**
     * Time-to-live in seconds for index data. Defaults to 259200 (3 days)
     *
     * @deprecated current schema uses default ttls. This parameter will be removed in Zipkin 2
     */
    @Deprecated
    public Builder indexTtl(int indexTtl) {
      this.indexTtl = indexTtl;
      return this;
    }

    /**
     * Indicates the maximum trace index metadata entries to cache. Zero disables the feature.
     * Defaults to 100000.
     *
     * <p>This is used to obviate redundant inserts into {@link Tables#SERVICE_NAME_INDEX}, {@link
     * Tables#SERVICE_SPAN_NAME_INDEX} and {@link Tables#ANNOTATIONS_INDEX}.
     *
     * <p>Corresponds to the count of rows inserted into between {@link #indexCacheTtl} and now.
     * This is bounded so that collectors that get large trace volume don't run out of memory before
     * {@link #indexCacheTtl} passes.
     *
     * <p>Note: It is hard to estimate precisely how many is the right number, particularly as
     * binary annotation values are included in partition keys (meaning each cache entry can vary in
     * size considerably). A good guess might be 5 x spans per indexCacheTtl, memory permitting.
     */
    public Builder indexCacheMax(int indexCacheMax) {
      this.indexCacheMax = indexCacheMax;
      return this;
    }

    /**
     * Indicates how long in seconds to cache trace index metadata. Defaults to 1 minute. This is
     * only read when {@link #indexCacheMax} is greater than zero.
     *
     * <p>You should pick a value that is longer than the gap between the root span's timestamp and
     * its latest descendant span's timestamp. More simply, if 95% of your trace durations are under
     * 1 minute, use 1 minute.
     */
    public Builder indexCacheTtl(int indexCacheTtl) {
      this.indexCacheTtl = indexCacheTtl;
      return this;
    }

    /**
     * How many more index rows to fetch than the user-supplied query limit. Defaults to 3.
     *
     * <p>Backend requests will request {@link QueryRequest#limit} times this factor rows from
     * Cassandra indexes in attempts to return {@link QueryRequest#limit} traces.
     *
     * <p>Indexing in cassandra will usually have more rows than trace identifiers due to factors
     * including table design and collection implementation. As there's no way to DISTINCT out
     * duplicates server-side, this over-fetches client-side when {@code indexFetchMultiplier} &gt; 1.
     */
    public Builder indexFetchMultiplier(int indexFetchMultiplier) {
      this.indexFetchMultiplier = indexFetchMultiplier;
      return this;
    }

    @Override public CassandraStorage build() {
      return new CassandraStorage(this);
    }

    Builder() {
    }
  }

  final int maxTraceCols;
  @Deprecated
  final int indexTtl;
  @Deprecated
  final int spanTtl;
  final int bucketCount;
  final String contactPoints;
  final int maxConnections;
  final String localDc;
  final String username;
  final String password;
  final boolean ensureSchema;
  final boolean useSsl;
  final String keyspace;
  final CacheBuilderSpec indexCacheSpec;
  final int indexFetchMultiplier;
  final boolean strictTraceId;
  final LazySession session;

  CassandraStorage(Builder builder) {
    this.contactPoints = builder.contactPoints;
    this.maxConnections = builder.maxConnections;
    this.localDc = builder.localDc;
    this.username = builder.username;
    this.password = builder.password;
    this.ensureSchema = builder.ensureSchema;
    this.useSsl = builder.useSsl;
    this.keyspace = builder.keyspace;
    this.maxTraceCols = builder.maxTraceCols;
    this.strictTraceId = builder.strictTraceId;
    this.indexTtl = builder.indexTtl;
    this.spanTtl = builder.spanTtl;
    this.bucketCount = builder.bucketCount;
    this.session = new LazySession(builder.sessionFactory, this);
    this.indexCacheSpec = builder.indexCacheMax == 0
        ? null
        : CacheBuilderSpec.parse("maximumSize=" + builder.indexCacheMax
            + ",expireAfterWrite=" + builder.indexCacheTtl + "s");
    this.indexFetchMultiplier = builder.indexFetchMultiplier;
  }

  /** Lazy initializes or returns the session in use by this storage component. */
  public Session session() {
    return session.get();
  }

  @Override protected CassandraSpanStore computeGuavaSpanStore() {
    return new CassandraSpanStore(session.get(), bucketCount, maxTraceCols,
        indexFetchMultiplier, strictTraceId);
  }

  @Override protected CassandraSpanConsumer computeGuavaSpanConsumer() {
    return new CassandraSpanConsumer(session.get(), bucketCount, spanTtl, indexTtl, indexCacheSpec);
  }

  @Override public CheckResult check() {
    try {
      session.get().execute(QueryBuilder.select("trace_id").from("traces").limit(1));
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override public void close() throws IOException {
    session.close();
  }

  /** Truncates all the column families, or throws on any failure. */
  @VisibleForTesting void clear() {
    guavaSpanConsumer().clear();
    List<ListenableFuture<?>> futures = new ArrayList<>();
    for (String cf : ImmutableList.of(
        "traces",
        "dependencies",
        Tables.SERVICE_NAMES,
        Tables.SPAN_NAMES,
        Tables.SERVICE_NAME_INDEX,
        Tables.SERVICE_SPAN_NAME_INDEX,
        Tables.ANNOTATIONS_INDEX
    )) {
      futures.add(session.get().executeAsync(format("TRUNCATE %s", cf)));
    }
    Futures.getUnchecked(Futures.allAsList(futures));
  }
}
