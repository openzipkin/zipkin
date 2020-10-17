/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.internal;

import com.datastax.driver.core.AuthProvider;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import zipkin2.internal.Nullable;

import static zipkin2.Call.propagateIfFatal;

public final class SessionBuilder {
  /** Returns a connected session. Closes the cluster if any exception occurred. */
  public static Session buildSession(
    String contactPoints,
    @Nullable String localDc,
    PoolingOptions poolingOptions,
    AuthProvider authProvider,
    boolean useSsl
  ) {
    // temporary: in Datastax Java Driver v4, there is only Session
    Cluster cluster = buildCluster(contactPoints, localDc, poolingOptions, authProvider, useSsl);
    try {
      return cluster.connect();
    } catch (Throwable e) {
      propagateIfFatal(e);
      cluster.close();
      throw e;
    }
  }

  // Visible for testing
  static Cluster buildCluster(
    String contactPoints,
    @Nullable String localDc,
    PoolingOptions poolingOptions,
    AuthProvider authProvider,
    boolean useSsl
  ) {
    Cluster.Builder builder = Cluster.builder().withoutJMXReporting();
    List<InetSocketAddress> contactPointsWithPorts = parseContactPoints(contactPoints);
    int defaultPort = findConnectPort(contactPointsWithPorts);
    builder.addContactPointsWithPorts(contactPointsWithPorts);
    builder.withPort(defaultPort); // This ends up protocolOptions.port
    builder.withAuthProvider(authProvider);
    builder.withRetryPolicy(ZipkinRetryPolicy.INSTANCE);
    builder.withLoadBalancingPolicy(
      new TokenAwarePolicy(
        new LatencyAwarePolicy.Builder(localDc != null
          ? DCAwareRoundRobinPolicy.builder().withLocalDc(localDc).build()
          : new RoundRobinPolicy()
          // This can select remote, but LatencyAwarePolicy will prefer local
        )
          .build()));
    builder.withPoolingOptions(poolingOptions);

    builder.withQueryOptions(
      new QueryOptions()
        // if local_dc isn't defined LOCAL_ONE incorrectly sticks to first seed host that connects
        .setConsistencyLevel(null != localDc ? ConsistencyLevel.LOCAL_ONE : ConsistencyLevel.ONE)
        .setDefaultIdempotence(true)); // all zipkin cql writes are idempotent

    if (useSsl) {
      builder = builder.withSSL();
    }

    return builder.build()
      // Ensures log categories can enable query logging
      .register(new QueryLogger.Builder().build());
  }

  static List<InetSocketAddress> parseContactPoints(String contactPoints) {
    List<InetSocketAddress> result = new ArrayList<>();
    for (String contactPoint : contactPoints.split(",", 100)) {
      HostAndPort parsed = HostAndPort.fromString(contactPoint, 9042);
      result.add(new InetSocketAddress(parsed.getHost(), parsed.getPort()));
    }
    return result;
  }

  /** Returns the consistent port across all contact points or 9042 */
  static int findConnectPort(List<InetSocketAddress> contactPoints) {
    Set<Integer> ports = new LinkedHashSet<>();
    for (InetSocketAddress contactPoint : contactPoints) {
      ports.add(contactPoint.getPort());
    }
    return ports.size() == 1 ? ports.iterator().next() : 9042;
  }
}
