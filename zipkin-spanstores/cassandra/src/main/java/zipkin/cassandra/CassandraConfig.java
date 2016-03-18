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
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy;
import zipkin.internal.Nullable;

import static zipkin.internal.Util.checkNotNull;

/** Configuration including defaults needed to run against a local Cassandra installation. */
public final class CassandraConfig {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String keyspace = "zipkin";
    private String contactPoints = "localhost";
    private String localDc;
    private int maxConnections = 8;
    private boolean ensureSchema = true;
    private String username;
    private String password;
    private int maxTraceCols = 100000;
    private int spanTtl = (int) TimeUnit.DAYS.toSeconds(7);
    private int indexTtl = (int) TimeUnit.DAYS.toSeconds(3);

    /** Keyspace to store span and index data. Defaults to "zipkin" */
    public Builder keyspace(String keyspace) {
      this.keyspace = keyspace;
      return this;
    }

    /** Comma separated list of hosts / IPs part of Cassandra cluster. Defaults to localhost */
    public Builder contactPoints(String contactPoints) {
      this.contactPoints = contactPoints;
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

    public CassandraConfig build() {
      return new CassandraConfig(this);
    }
  }

  final String keyspace;
  final int maxTraceCols;
  final int indexTtl;
  final int spanTtl;
  final String contactPoints;
  final int maxConnections;
  final boolean ensureSchema;
  final String localDc;
  final String username;
  final String password;

  CassandraConfig(Builder builder) {
    this.keyspace = checkNotNull(builder.keyspace, "keyspace");
    this.maxTraceCols = builder.maxTraceCols;
    this.indexTtl = builder.indexTtl;
    this.spanTtl = builder.spanTtl;
    this.contactPoints = checkNotNull(builder.contactPoints, "contactPoints");
    this.maxConnections = builder.maxConnections;
    this.ensureSchema = builder.ensureSchema;
    this.localDc = builder.localDc;
    this.username = builder.username;
    this.password = builder.password;
  }

  Cluster toCluster() {
    Cluster.Builder builder = Cluster.builder();
    List<InetSocketAddress> contactPoints = parseContactPoints();
    int defaultPort = findConnectPort(contactPoints);
    builder.addContactPointsWithPorts(contactPoints);
    builder.withPort(defaultPort); // This ends up protocolOptions.port
    if (username != null && password != null) {
      builder.withCredentials(username, password);
    }
    builder.withRetryPolicy(ZipkinRetryPolicy.INSTANCE);
    builder.withLoadBalancingPolicy(new TokenAwarePolicy(new LatencyAwarePolicy.Builder(
        localDc != null
            ? DCAwareRoundRobinPolicy.builder().withLocalDc(localDc).build()
            : new RoundRobinPolicy() // This can select remote, but LatencyAwarePolicy will prefer local
    ).build()));
    builder.withPoolingOptions(new PoolingOptions().setMaxConnectionsPerHost(
        HostDistance.LOCAL, maxConnections
    ));
    return builder.build();
  }

  List<InetSocketAddress> parseContactPoints() {
    List<InetSocketAddress> result = new LinkedList<>();
    for (String contactPoint : contactPoints.split(",")) {
      HostAndPort parsed = HostAndPort.fromString(contactPoint);
      result.add(
          new InetSocketAddress(parsed.getHostText(), parsed.getPortOrDefault(9042)));
    }
    return result;
  }

  /** Returns the consistent port across all contact points or 9042 */
  static int findConnectPort(List<InetSocketAddress> contactPoints) {
    Set<Integer> ports = Sets.newLinkedHashSet();
    for (InetSocketAddress contactPoint : contactPoints) {
      ports.add(contactPoint.getPort());
    }
    return ports.size() == 1 ? ports.iterator().next() : 9042;
  }
}
