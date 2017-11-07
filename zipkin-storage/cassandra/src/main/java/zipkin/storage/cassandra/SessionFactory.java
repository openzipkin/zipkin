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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.net.HostAndPort;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Creates a session and ensures schema if configured. Closes the cluster and session if any
 * exception occurred.
 */
public interface SessionFactory {

  Session create(CassandraStorage storage);

  final class Default implements SessionFactory {

    /**
     * Creates a session and ensures schema if configured. Closes the cluster and session if any
     * exception occurred.
     */
    @Override public Session create(CassandraStorage cassandra) {
      Closer closer = Closer.create();
      try {
        Cluster cluster = closer.register(buildCluster(cassandra));
        cluster.register(new QueryLogger.Builder().build());
        if (cassandra.ensureSchema) {
          Session session = closer.register(cluster.connect());
          Schema.ensureExists(cassandra.keyspace, session);
          session.execute("USE " + cassandra.keyspace);
          return session;
        } else {
          return cluster.connect(cassandra.keyspace);
        }
      } catch (RuntimeException e) {
        try {
          closer.close();
        } catch (IOException ignored) {
        }
        throw e;
      }
    }

    // Visible for testing
    static Cluster buildCluster(CassandraStorage cassandra) {
      Cluster.Builder builder = Cluster.builder();
      List<InetSocketAddress> contactPoints = parseContactPoints(cassandra);
      int defaultPort = findConnectPort(contactPoints);
      builder.addContactPointsWithPorts(contactPoints);
      builder.withPort(defaultPort); // This ends up protocolOptions.port
      if (cassandra.username != null && cassandra.password != null) {
        builder.withCredentials(cassandra.username, cassandra.password);
      }
      builder.withRetryPolicy(ZipkinRetryPolicy.INSTANCE);
      builder.withLoadBalancingPolicy(new TokenAwarePolicy(new LatencyAwarePolicy.Builder(
          cassandra.localDc != null
              ? DCAwareRoundRobinPolicy.builder().withLocalDc(cassandra.localDc).build()
              : new RoundRobinPolicy()
          // This can select remote, but LatencyAwarePolicy will prefer local
      ).build()));
      builder.withPoolingOptions(new PoolingOptions().setMaxConnectionsPerHost(
          HostDistance.LOCAL, cassandra.maxConnections
      ));
      if (cassandra.useSsl) {
        builder = builder.withSSL();
      }
      return builder.build();
    }

    static List<InetSocketAddress> parseContactPoints(CassandraStorage cassandra) {
      List<InetSocketAddress> result = new ArrayList<>();
      for (String contactPoint : cassandra.contactPoints.split(",")) {
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
}
