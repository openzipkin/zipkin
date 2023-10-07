/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.storage.cassandra;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InternetProtocol;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

import static org.testcontainers.utility.DockerImageName.parse;

public class CassandraExtension implements BeforeAllCallback, AfterAllCallback {
  static final Logger LOGGER = LoggerFactory.getLogger(CassandraExtension.class);
  static final int CASSANDRA_PORT = 9042;

  final CassandraContainer container = new CassandraContainer();

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    container.stop();
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.start();
    LOGGER.info("Using bootstrapServer " + bootstrapServer());
  }

  String bootstrapServer() {
    return container.getHost() + ":" + container.getMappedPort(CASSANDRA_PORT);
  }

  static final class CassandraContainer extends GenericContainer<CassandraContainer> {
    CassandraContainer() {
      super(parse("cassandra:4.1.3"));
      if ("true".equals(System.getProperty("docker.skip"))) {
        throw new TestAbortedException("${docker.skip} == true");
      }
      waitStrategy = Wait.forSuccessfulCommand("cqlsh -e \"describe keyspaces\"").withStartupTimeout(Duration.ofMinutes(5));
      addFixedExposedPort(CASSANDRA_PORT, CASSANDRA_PORT, InternetProtocol.TCP);
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }
  }
}
