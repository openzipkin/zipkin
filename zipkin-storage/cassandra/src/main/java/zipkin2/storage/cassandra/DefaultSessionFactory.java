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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.datastax.driver.mapping.MappingManager;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.net.HostAndPort;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.storage.cassandra.Schema.AnnotationUDT;
import zipkin2.storage.cassandra.Schema.EndpointUDT;

/**
 * Creates a session and ensures schema if configured. Closes the cluster and session if any
 * exception occurred.
 */
final class DefaultSessionFactory implements CassandraStorage.SessionFactory {
  private static final Logger LOG = LoggerFactory.getLogger(Schema.class);

  /**
   * Creates a session and ensures schema if configured. Closes the cluster and session if any
   * exception occurred.
   */
  @Override
  public Session create(CassandraStorage cassandra) {
    Closer closer = Closer.create();
    try {
      Cluster cluster = closer.register(buildCluster(cassandra));
      cluster.register(new QueryLogger.Builder().build());
      Session session;
      String keyspace = cassandra.keyspace();
      if (cassandra.ensureSchema()) {
        session = closer.register(cluster.connect());
        Schema.ensureExists(keyspace, cassandra.searchEnabled(), session);
        session.execute("USE " + keyspace);
      } else {
        LOG.debug("Skipping schema check on keyspace {} as ensureSchema was false", keyspace);
        session = cluster.connect(keyspace);
      }

      initializeUDTs(session);

      return session;
    } catch (RuntimeException e) {
      try {
        closer.close();
      } catch (IOException ignored) {
      }
      throw e;
    }
  }

  private static void initializeUDTs(Session session) {
    MappingManager mapping = new MappingManager(session);
    String keyspace = session.getLoggedKeyspace();
    LOG.debug("Registering endpoint and annotation UDTs to keyspace {}", keyspace);
    mapping.udtCodec(EndpointUDT.class, keyspace);
    mapping.udtCodec(AnnotationUDT.class, keyspace);
  }

  // Visible for testing
  static Cluster buildCluster(CassandraStorage cassandra) {
    Cluster.Builder builder = Cluster.builder().withoutJMXReporting();
    List<InetSocketAddress> contactPoints = parseContactPoints(cassandra);
    int defaultPort = findConnectPort(contactPoints);
    builder.addContactPointsWithPorts(contactPoints);
    builder.withPort(defaultPort); // This ends up protocolOptions.port
    if (cassandra.username() != null && cassandra.password() != null) {
      builder.withCredentials(cassandra.username(), cassandra.password());
    }
    builder.withRetryPolicy(ZipkinRetryPolicy.INSTANCE);
    builder.withLoadBalancingPolicy(
        new TokenAwarePolicy(
            new LatencyAwarePolicy.Builder(
                    cassandra.localDc() != null
                        ? DCAwareRoundRobinPolicy.builder().withLocalDc(cassandra.localDc()).build()
                        : new RoundRobinPolicy()
                    // This can select remote, but LatencyAwarePolicy will prefer local
                    )
                .build()));
    builder.withPoolingOptions(cassandra.poolingOptions());

    builder.withQueryOptions(
        new QueryOptions()
            // if local_dc isn't defined LOCAL_ONE incorrectly sticks to first seed host that
            // connects
            .setConsistencyLevel(
                null != cassandra.localDc() ? ConsistencyLevel.LOCAL_ONE : ConsistencyLevel.ONE)
            // all zipkin cql writes are idempotent
            .setDefaultIdempotence(true));

    if (cassandra.useSsl()) {
      builder = builder.withSSL();
    }

    return builder.build();
  }

  static List<InetSocketAddress> parseContactPoints(CassandraStorage cassandra) {
    List<InetSocketAddress> result = new ArrayList<>();
    for (String contactPoint : cassandra.contactPoints().split(",")) {
      HostAndPort parsed = HostAndPort.fromString(contactPoint);
      result.add(new InetSocketAddress(parsed.getHost(), parsed.getPortOrDefault(9042)));
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
