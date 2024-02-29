/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import zipkin2.internal.Nullable;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.StorageComponent;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONNECTION_MAX_REQUESTS;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE;

abstract class CassandraStorageBuilder<B extends CassandraStorageBuilder<B>>
  extends StorageComponent.Builder {

  CassandraStorage.SessionFactory sessionFactory = CassandraStorage.SessionFactory.DEFAULT;
  boolean strictTraceId = true, searchEnabled = true;
  Set<String> autocompleteKeys = Set.of();
  int autocompleteTtl = (int) TimeUnit.HOURS.toMillis(1);
  int autocompleteCardinality = 5 * 4000; // Ex. 5 site tags with cardinality 4000 each

  String contactPoints = "localhost";
  // Driver v4 requires this, so take a guess! When we are wrong, the user can override anyway
  String localDc = "datacenter1";
  @Nullable String username, password;
  boolean useSsl = false;
  boolean sslHostnameValidation = true;

  String keyspace;
  BiFunction<CassandraStorage, CqlSession, Schema.Metadata> ensureSchema = Schema::ensure;

  int maxTraceCols = 100_000;
  int indexFetchMultiplier = 3;

  // Zipkin collectors can create out a lot of async requests in bursts, so we
  // increase some properties beyond the norm.
  /** @see DefaultDriverOption#CONNECTION_POOL_LOCAL_SIZE */
  // Ported from java-driver v3 PoolingOptions.setMaxConnectionsPerHost(HostDistance.LOCAL, 8)
  int poolLocalSize = 8;
  /** @see DefaultDriverOption#CONNECTION_MAX_REQUESTS */
  // Ported from java-driver v3 PoolingOptions.setMaxQueueSize(40960)
  final int maxRequestsPerConnection = 40960 / poolLocalSize;

  Map<DriverOption, Integer> poolingOptions() {
    Map<DriverOption, Integer> result = new LinkedHashMap<>();
    result.put(CONNECTION_POOL_LOCAL_SIZE, poolLocalSize);
    result.put(CONNECTION_MAX_REQUESTS, maxRequestsPerConnection);
    return result;
  }

  CassandraStorageBuilder(String defaultKeyspace) {
    keyspace = defaultKeyspace;
  }

  @Override public B strictTraceId(boolean strictTraceId) {
    this.strictTraceId = strictTraceId;
    return (B) this;
  }

  @Override public B searchEnabled(boolean searchEnabled) {
    this.searchEnabled = searchEnabled;
    return (B) this;
  }

  @Override public B autocompleteKeys(List<String> keys) {
    if (keys == null) throw new NullPointerException("keys == null");
    this.autocompleteKeys = Set.copyOf(keys);
    return (B) this;
  }

  @Override public B autocompleteTtl(int autocompleteTtl) {
    if (autocompleteTtl <= 0) throw new IllegalArgumentException("autocompleteTtl <= 0");
    this.autocompleteTtl = autocompleteTtl;
    return (B) this;
  }

  @Override public B autocompleteCardinality(int autocompleteCardinality) {
    if (autocompleteCardinality <= 0) {
      throw new IllegalArgumentException("autocompleteCardinality <= 0");
    }
    this.autocompleteCardinality = autocompleteCardinality;
    return (B) this;
  }

  /**
   * Comma separated list of host addresses part of Cassandra cluster. You can also specify a custom
   * port with 'host:port'. Defaults to localhost on port 9042 *
   */
  public B contactPoints(String contactPoints) {
    if (contactPoints == null) throw new NullPointerException("contactPoints == null");
    this.contactPoints = contactPoints;
    return (B) this;
  }

  /**
   * Name of the datacenter that will be considered "local" for latency load balancing. When unset,
   * load-balancing is round-robin.
   */
  public B localDc(String localDc) {
    if (localDc == null) throw new NullPointerException("localDc == null");
    this.localDc = localDc;
    return (B) this;
  }

  /** Max pooled connections per datacenter-local host. Defaults to 8 */
  public B maxConnections(int maxConnections) {
    if (maxConnections <= 0) throw new IllegalArgumentException("maxConnections <= 0");
    this.poolLocalSize = maxConnections;
    return (B) this;
  }

  /** Will throw an exception on startup if authentication fails. No default. */
  public B username(@Nullable String username) {
    this.username = username;
    return (B) this;
  }

  /** Will throw an exception on startup if authentication fails. No default. */
  public B password(@Nullable String password) {
    this.password = password;
    return (B) this;
  }

  /** Use ssl for connection. Defaults to false. */
  public B useSsl(boolean useSsl) {
    this.useSsl = useSsl;
    return (B) this;
  }

  /** Controls validation of Cassandra server hostname. Defaults to true. */
  public B sslHostnameValidation(boolean sslHostnameValidation) {
    this.sslHostnameValidation = sslHostnameValidation;
    return (B) this;
  }

  /** Keyspace to store span and index data. Defaults to "zipkin3" */
  public B keyspace(String keyspace) {
    if (keyspace == null) throw new NullPointerException("keyspace == null");
    this.keyspace = keyspace;
    return (B) this;
  }

  /** Override to control how sessions are created. */
  public B sessionFactory(CassandraStorage.SessionFactory sessionFactory) {
    if (sessionFactory == null) throw new NullPointerException("sessionFactory == null");
    this.sessionFactory = sessionFactory;
    return (B) this;
  }

  public B ensureSchema(boolean ensureSchema) {
    if (ensureSchema) {
      this.ensureSchema = Schema::ensure;
    } else {
      this.ensureSchema = Schema::validate;
    }
    return (B) this;
  }

  /**
   * Spans have multiple values for the same id. For example, a client and server contribute to the
   * same span id. When searching for spans by id, the amount of results may be larger than the ids.
   * This defines a threshold which accommodates this situation, without looking for an unbounded
   * number of results.
   */
  public B maxTraceCols(int maxTraceCols) {
    if (maxTraceCols <= 0) throw new IllegalArgumentException("maxTraceCols <= 0");
    this.maxTraceCols = maxTraceCols;
    return (B) this;
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
  public B indexFetchMultiplier(int indexFetchMultiplier) {
    if (indexFetchMultiplier <= 0) throw new IllegalArgumentException("indexFetchMultiplier <= 0");
    this.indexFetchMultiplier = indexFetchMultiplier;
    return (B) this;
  }
}
