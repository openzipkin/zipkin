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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.Session;
import com.google.common.io.Closer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CassandraStorageExtension implements BeforeAllCallback, AfterAllCallback {
  static final Logger LOGGER = LoggerFactory.getLogger(CassandraStorageExtension.class);
  static final int CASSANDRA_PORT = 9042;
  final String image;
  CassandraContainer container;
  Session session;
  Closer closer = Closer.create();

  CassandraStorageExtension(String image) {
    this.image = image;
  }

  Session session() {
    return session;
  }

  @Override public void beforeAll(ExtensionContext context) throws Exception {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    if (!"true".equals(System.getProperty("docker.skip"))) {
      try {
        LOGGER.info("Starting docker image " + image);
        container = new CassandraContainer(image).withExposedPorts(CASSANDRA_PORT);
        container.start();
      } catch (RuntimeException e) {
        LOGGER.warn("Couldn't start docker image " + image + ": " + e.getMessage(), e);
      }
    } else {
      LOGGER.info("Skipping startup of docker " + image);
    }

    try {
      session = tryToInitializeSession();
    } catch (RuntimeException | Error e) {
      if (container == null) throw e;
      LOGGER.warn("Couldn't connect to docker image " + image + ": " + e.getMessage(), e);
      container.stop();
      container = null; // try with local connection instead
      session = tryToInitializeSession();
    }
    closer.register(session);
  }

  Session tryToInitializeSession() throws IOException {
    Cluster cluster = closer.register(getCluster(contactPoint()));
    Session session = closer.register(cluster.newSession());
    try {
      session.execute("SELECT now() FROM system.local");
    } catch (RuntimeException e) {
      closer.close();
      assumeTrue(false, e.getMessage());
    }
    return session;
  }

  CassandraStorage.Builder computeStorageBuilder() {
    InetSocketAddress contactPoint = contactPoint();
    return CassandraStorage.newBuilder()
        .contactPoints(contactPoint.getHostString() + ":" + contactPoint.getPort())
        .ensureSchema(true)
        .keyspace("test_cassandra3");
  }

  InetSocketAddress contactPoint() {
    if (container != null && container.isRunning()) {
      return new InetSocketAddress(
          container.getContainerIpAddress(), container.getMappedPort(CASSANDRA_PORT));
    } else {
      return new InetSocketAddress("127.0.0.1", CASSANDRA_PORT);
    }
  }

  @Override public void afterAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }
    try {
      closer.close();
    } catch (Exception | Error e) {
      LOGGER.warn("error closing session " + e.getMessage(), e);
    } finally {
      if (container != null) {
        LOGGER.info("Stopping docker image " + image);
        container.stop();
      }
    }
  }

  static final class CassandraContainer extends GenericContainer<CassandraContainer> {
    CassandraContainer(String image) {
      super(image);
    }

    @Override
    protected void waitUntilContainerStarted() {
      Unreliables.retryUntilSuccess(
          120,
          TimeUnit.SECONDS,
          () -> {
            if (!isRunning()) {
              throw new ContainerLaunchException("Container failed to start");
            }

            InetSocketAddress address =
              new InetSocketAddress(getContainerIpAddress(), getMappedPort(9042));

            try (Cluster cluster = getCluster(address);
                Session session = cluster.newSession()) {
              session.execute("SELECT now() FROM system.local");
              logger().info("Obtained a connection to container ({})", cluster.getClusterName());
              return null; // unused value
            }
          });
    }
  }

  static Cluster getCluster(InetSocketAddress contactPoint) {
    return Cluster.builder().withoutJMXReporting()
        .addContactPointsWithPorts(contactPoint)
        .withRetryPolicy(ZipkinRetryPolicy.INSTANCE)
        .withPoolingOptions(new PoolingOptions().setMaxConnectionsPerHost(HostDistance.LOCAL, 1))
        .build();
  }
}
