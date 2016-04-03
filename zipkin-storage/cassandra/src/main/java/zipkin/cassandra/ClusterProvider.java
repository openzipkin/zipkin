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
import org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy;

final class ClusterProvider {
  final String contactPoints;
  final int maxConnections;
  final String localDc;
  final String username;
  final String password;

  ClusterProvider(CassandraStorage.Builder builder) {
    this.contactPoints = builder.contactPoints;
    this.maxConnections = builder.maxConnections;
    this.localDc = builder.localDc;
    this.username = builder.username;
    this.password = builder.password;
  }

  Cluster get() {
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
            : new RoundRobinPolicy()
        // This can select remote, but LatencyAwarePolicy will prefer local
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
