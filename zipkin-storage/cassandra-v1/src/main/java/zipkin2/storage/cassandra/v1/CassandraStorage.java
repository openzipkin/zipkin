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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.cache.CacheBuilderSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import zipkin2.CheckResult;
import zipkin2.internal.Nullable;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.cassandra.internal.call.DeduplicatingVoidCallFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * CQL3 implementation of zipkin storage.
 *
 * <p>Queries are logged to the category "com.datastax.driver.core.QueryLogger" when debug or trace
 * is enabled via SLF4J. Trace level includes bound values.
 *
 * <p>Redundant requests to store service or span names are ignored for an hour to reduce load. This
 * feature is implemented by {@link DeduplicatingVoidCallFactory}.
 *
 * <p>Schema is installed by default from "/cassandra-schema-cql3.txt"
 */
public final class CassandraStorage extends StorageComponent {

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder extends StorageComponent.Builder {
    boolean strictTraceId = true, searchEnabled = true;
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
    List<String> autocompleteKeys = new ArrayList<>();
    int autocompleteTtl = (int) TimeUnit.HOURS.toMillis(1);
    int autocompleteCardinality = 5 * 4000; // Ex. 5 site tags with cardinality 4000 each

    /**
     * Used to avoid hot spots when writing indexes used to query by service name or annotation.
     *
     * <p>This controls the amount of buckets, or partitions writes to {@code service_name_index}
     * and {@code annotations_index}. This must be the same for all query servers, and has
     * historically always been 10.
     *
     * <p>See https://github.com/openzipkin/zipkin/issues/623 for further explanation
     */
    int bucketCount = 10;

    int spanTtl = (int) TimeUnit.DAYS.toSeconds(7);
    int indexTtl = (int) TimeUnit.DAYS.toSeconds(3);
    SessionFactory sessionFactory = new SessionFactory.Default();

    /** {@inheritDoc} */
    @Override
    public Builder strictTraceId(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
      return this;
    }

    @Override
    public Builder searchEnabled(boolean searchEnabled) {
      this.searchEnabled = searchEnabled;
      return this;
    }

    @Override public Builder autocompleteKeys(List<String> keys) {
      if (keys == null) throw new NullPointerException("keys == null");
      this.autocompleteKeys = keys;
      return this;
    }

    @Override public Builder autocompleteTtl(int autocompleteTtl) {
      if (autocompleteTtl <= 0) throw new IllegalArgumentException("autocompleteTtl <= 0");
      this.autocompleteTtl = autocompleteTtl;
      return this;
    }

    @Override public Builder autocompleteCardinality(int autocompleteCardinality) {
      if (autocompleteCardinality <= 0) throw new IllegalArgumentException("autocompleteCardinality <= 0");
      this.autocompleteCardinality = autocompleteCardinality;
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

    /**
     * Comma separated list of host addresses part of Cassandra cluster. You can also specify a
     * custom port with 'host:port'. Defaults to localhost on port 9042 *
     */
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
     * Ensures that schema exists, if enabled tries to execute script
     * io.zipkin:zipkin-cassandra-core/cassandra-schema-cql3.txt. Defaults to true.
     */
    public Builder ensureSchema(boolean ensureSchema) {
      this.ensureSchema = ensureSchema;
      return this;
    }

    /** Use ssl for connection. Defaults to false. */
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
     * <p>Backend requests will request {@link QueryRequest#limit()} times this factor rows from
     * Cassandra indexes in attempts to return {@link QueryRequest#limit()} traces.
     *
     * <p>Indexing in cassandra will usually have more rows than trace identifiers due to factors
     * including table design and collection implementation. As there's no way to DISTINCT out
     * duplicates server-side, this over-fetches client-side when {@code indexFetchMultiplier} &gt;
     * 1.
     */
    public Builder indexFetchMultiplier(int indexFetchMultiplier) {
      this.indexFetchMultiplier = indexFetchMultiplier;
      return this;
    }

    @Override
    public CassandraStorage build() {
      return new CassandraStorage(this);
    }

    Builder() {}
  }

  final int maxTraceCols;
  @Deprecated final int indexTtl;
  @Deprecated final int spanTtl;
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
  final boolean strictTraceId, searchEnabled;
  final LazySession session;
  final List<String> autocompleteKeys;
  final int autocompleteTtl;
  final int autocompleteCardinality;

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  volatile CassandraSpanConsumer spanConsumer;
  volatile CassandraSpanStore spanStore;
  volatile CassandraAutocompleteTags tagStore;

  CassandraStorage(Builder b) {
    this.contactPoints = b.contactPoints;
    this.maxConnections = b.maxConnections;
    this.localDc = b.localDc;
    this.username = b.username;
    this.password = b.password;
    this.ensureSchema = b.ensureSchema;
    this.useSsl = b.useSsl;
    this.keyspace = b.keyspace;
    this.maxTraceCols = b.maxTraceCols;
    this.strictTraceId = b.strictTraceId;
    this.searchEnabled = b.searchEnabled;
    this.indexTtl = b.indexTtl;
    this.spanTtl = b.spanTtl;
    this.bucketCount = b.bucketCount;
    this.session = new LazySession(b.sessionFactory, this);
    if (b.indexCacheMax != 0) {
      this.indexCacheSpec =
          CacheBuilderSpec.parse(
              "maximumSize=" + b.indexCacheMax + ",expireAfterWrite=" + b.indexCacheTtl + "s");
    } else {
      this.indexCacheSpec = null;
    }
    this.indexFetchMultiplier = b.indexFetchMultiplier;
    this.autocompleteKeys = b.autocompleteKeys;
    this.autocompleteTtl = b.autocompleteTtl;
    this.autocompleteCardinality = b.autocompleteCardinality;
  }

  /** Lazy initializes or returns the session in use by this storage component. */
  Session session() {
    return session.get();
  }

  Schema.Metadata metadata() {
    return session.metadata();
  }

  /** {@inheritDoc} Memoized in order to avoid re-preparing statements */
  @Override public SpanStore spanStore() {
    if (spanStore == null) {
      synchronized (this) {
        if (spanStore == null) {
          spanStore = new CassandraSpanStore(this);
        }
      }
    }
    return spanStore;
  }

  @Override public ServiceAndSpanNames serviceAndSpanNames() {
    return (ServiceAndSpanNames) spanStore();
  }

  @Override public AutocompleteTags autocompleteTags() {
    if (tagStore == null) {
      synchronized (this) {
        if (tagStore == null) {
          tagStore = new CassandraAutocompleteTags(this);
        }
      }
    }
    return tagStore;
  }

  /** {@inheritDoc} Memoized in order to avoid re-preparing statements */
  @Override
  public SpanConsumer spanConsumer() {
    if (spanConsumer == null) {
      synchronized (this) {
        if (spanConsumer == null) {
          spanConsumer = new CassandraSpanConsumer(this, indexCacheSpec);
        }
      }
    }
    return spanConsumer;
  }

  @Override
  public CheckResult check() {
    if (closeCalled) throw new IllegalStateException("closed");
    try {
      session.get().execute(QueryBuilder.select("trace_id").from("traces").limit(1));
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override
  public void close() {
    if (closeCalled) return;
    session.close();
    CassandraSpanConsumer maybeConsumer = spanConsumer;
    if (maybeConsumer != null) maybeConsumer.clear();
    closeCalled = true;
  }
}
