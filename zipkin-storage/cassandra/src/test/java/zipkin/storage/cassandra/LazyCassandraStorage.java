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
import com.datastax.driver.core.Session;
import com.google.common.net.HostAndPort;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.traits.LinkableContainer;
import zipkin.Component;
import zipkin.internal.LazyCloseable;

class LazyCassandraStorage extends LazyCloseable<CassandraStorage> implements TestRule {

  final String image;
  final String keyspace;

  GenericContainer container;

  LazyCassandraStorage(String image, String keyspace) {
    this.image = image;
    this.keyspace = keyspace;
  }

  @Override protected CassandraStorage compute() {
    try {
      container = new CassandraContainer(image).withExposedPorts(9042);
      container.start();
      System.out.println("Will use TestContainers Cassandra instance");
    } catch (Exception e) {
      // Ignore
    }

    CassandraStorage result = computeStorageBuilder().build();
    Component.CheckResult check = result.check();
    if (check.ok) return result;
    throw new AssumptionViolatedException(check.exception.getMessage(), check.exception);
  }

  CassandraStorage.Builder computeStorageBuilder() {
    return CassandraStorage.builder()
        .contactPoints(contactPoints())
        .maxConnections(2)
        .keyspace(keyspace);
  }

  String contactPoints() {
    if (container != null && container.isRunning()) {
      return container.getContainerIpAddress() + ":" + container.getMappedPort(9042);
    } else {
      return "127.0.0.1:9042";
    }
  }

  @Override public void close() {
    try {
      CassandraStorage storage = maybeNull();
      if (storage != null) storage.close();
    } catch (IOException ioe) {
      // Ignore
    } finally {
      if (container != null) container.stop();
    }
  }

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        get();
        try {
          base.evaluate();
        } finally {
          close();
        }
      }
    };
  }

  private static class CassandraContainer extends GenericContainer<CassandraContainer> implements
      LinkableContainer {

    CassandraContainer(String image) {
      super(image);
    }

    @Override protected void waitUntilContainerStarted() {
      Unreliables.retryUntilSuccess(120, TimeUnit.SECONDS, () -> {
        if (!isRunning()) {
          throw new ContainerLaunchException("Container failed to start");
        }

        try (Cluster cluster = getCluster(); Session session = cluster.newSession()) {
          session.execute("SELECT now() FROM system.local");
          logger().info("Obtained a connection to container ({})", cluster.getClusterName());
          return null; // unused value
        }
      });
    }

    private Cluster getCluster() {
      HostAndPort hap = HostAndPort.fromParts(getContainerIpAddress(), getMappedPort(9042));
      InetSocketAddress address = new InetSocketAddress(hap.getHostText(), hap.getPort());

      return Cluster.builder()
          .addContactPointsWithPorts(address)
          .withRetryPolicy(ZipkinRetryPolicy.INSTANCE)
          .withPoolingOptions(new PoolingOptions().setMaxConnectionsPerHost(HostDistance.LOCAL, 1))
          .build();
    }
  }
}
