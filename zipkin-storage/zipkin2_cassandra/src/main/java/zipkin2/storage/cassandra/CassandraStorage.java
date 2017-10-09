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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nullable;
import zipkin2.CheckResult;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

/**
 * CQL3 implementation of zipkin storage.
 *
 * <p>Queries are logged to the category "com.datastax.driver.core.QueryLogger" when debug or trace
 * is enabled via SLF4J. Trace level includes bound values.
 *
 * <p>Schema is installed by default from "/zipkin2_cassandra-schema.cql"
 */
@AutoValue
public abstract class CassandraStorage extends StorageComponent {

  // @FunctionalInterface, except safe for lower language levels
  public interface SessionFactory {
    SessionFactory DEFAULT = new DefaultSessionFactory();

    Session create(CassandraStorage storage);
  }

  public static Builder newBuilder() {
    return new AutoValue_CassandraStorage.Builder()
      .strictTraceId(true)
      .keyspace(Schema.DEFAULT_KEYSPACE)
      .contactPoints("localhost")
      .maxConnections(8)
      .ensureSchema(true)
      .useSsl(false)
      .maxTraceCols(100000)
      .indexFetchMultiplier(3)
      .sessionFactory(SessionFactory.DEFAULT);
  }

  @AutoValue.Builder
  public static abstract class Builder extends StorageComponent.Builder {
    /** {@inheritDoc} */
    @Override public abstract Builder strictTraceId(boolean strictTraceId);

    /** Override to control how sessions are created. */
    public abstract Builder sessionFactory(SessionFactory sessionFactory);

    /** Keyspace to store span and index data. Defaults to "zipkin3" */
    public abstract Builder keyspace(String keyspace);

    /** Comma separated list of host addresses part of Cassandra cluster. You can also specify a custom port with 'host:port'. Defaults to localhost on port 9042 **/
    public abstract Builder contactPoints(String contactPoints);
    /**
     * Name of the datacenter that will be considered "local" for latency load balancing. When
     * unset, load-balancing is round-robin.
     */
    public abstract Builder localDc(@Nullable String localDc);

    /** Max pooled connections per datacenter-local host. Defaults to 8 */
    public abstract Builder maxConnections(int maxConnections);

    /**
     * Ensures that schema exists, if enabled tries to execute script io.zipkin:zipkin-cassandra-core/cassandra-schema-cql3.txt.
     * Defaults to true.
     */
    public abstract Builder ensureSchema(boolean ensureSchema);

    /**
     * Use ssl for driver
     * Defaults to false.
     */
    public abstract Builder useSsl(boolean useSsl);

    /** Will throw an exception on startup if authentication fails. No default. */
    public abstract Builder username(@Nullable String username);

    /** Will throw an exception on startup if authentication fails. No default. */
    public abstract Builder password(@Nullable String password);

    /**
     * Spans have multiple values for the same id. For example, a client and server contribute to
     * the same span id. When searching for spans by id, the amount of results may be larger than
     * the ids. This defines a threshold which accommodates this situation, without looking for an
     * unbounded number of results.
     */
    public abstract Builder maxTraceCols(int maxTraceCols);

    /**
     * How many more index rows to fetch than the user-supplied query limit. Defaults to 3.
     *
     * <p>Backend requests will request {@link QueryRequest#limit} times this factor rows from
     * Cassandra indexes in attempts to return {@link QueryRequest#limit} traces.
     *
     * <p>Indexing in cassandra will usually have more rows than trace identifiers due to factors
     * including table design and collection implementation. As there's no way to DISTINCT out
     * duplicates server-side, this over-fetches client-side when {@code indexFetchMultiplier} > 1.
     */
    public abstract Builder indexFetchMultiplier(int indexFetchMultiplier);

    @Override abstract public CassandraStorage build();

    Builder() {
    }
  }

  abstract int maxTraceCols();
  abstract String contactPoints();
  abstract int maxConnections();
  @Nullable abstract String localDc();
  @Nullable abstract String username();
  @Nullable abstract String password();
  abstract boolean ensureSchema();
  abstract boolean useSsl();
  abstract String keyspace();
  abstract int indexFetchMultiplier();
  abstract boolean strictTraceId();
  abstract SessionFactory sessionFactory();

  /** session and close are typically called from different threads */
  volatile boolean provisioned, closeCalled;

  /** Lazy initializes or returns the session in use by this storage component. */
  @Memoized Session session() {
    Session result = sessionFactory().create(this);
    provisioned = true;
    return result;
  }

  /** {@inheritDoc} Memoized in order to avoid re-preparing statements */
  @Memoized @Override public SpanStore spanStore() {
    return new CassandraSpanStore(this);
  }

  /** {@inheritDoc} Memoized in order to avoid re-preparing statements */
  @Memoized @Override public SpanConsumer spanConsumer() {
    return new CassandraSpanConsumer(this);
  }

  @Override public CheckResult check() {
    try {
      if (closeCalled) throw new IllegalStateException("closed");
      session().execute(QueryBuilder.select("trace_id").from("span").limit(1));
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
    return CheckResult.OK;
  }

  @Override public synchronized void close() {
    if (closeCalled) return;
    if (provisioned) session().close();
    closeCalled = true;
  }

  abstract Builder toBuilder(); // initially visible for testing. we might promote it later

  /** Truncates all the column families, or throws on any failure. */
  @VisibleForTesting void clear() {
    for (String cf : ImmutableList.of(
      Schema.TABLE_SPAN,
      Schema.TABLE_TRACE_BY_SERVICE_SPAN,
      Schema.TABLE_SERVICE_SPANS,
      Schema.TABLE_DEPENDENCY
    )) {
      session().execute("TRUNCATE " + cf);
    }
  }

  CassandraStorage() {
  }
}
