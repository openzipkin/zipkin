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
package zipkin2.collector.rabbitmq;

import java.time.Duration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static java.util.Arrays.asList;
import static org.testcontainers.utility.DockerImageName.parse;
import static zipkin2.Call.propagateIfFatal;

class RabbitMQExtension implements BeforeAllCallback, AfterAllCallback {
  static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQExtension.class);
  static final int RABBIT_PORT = 5672;

  RabbitMQContainer container = new RabbitMQContainer();

  @Override public void beforeAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.start();
    LOGGER.info("Using hostPort " + host() + ":" + port());
  }

  @Override public void afterAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.stop();
  }

  RabbitMQCollector.Builder newCollectorBuilder(String queue) {
    declareQueue(queue);
    return RabbitMQCollector.builder().queue(queue).addresses(asList(host() + ":" + port()));
  }

  void declareQueue(String queue) {
    ExecResult result;
    try {
      result = container.execInContainer("rabbitmqadmin", "declare", "queue", "name=" + queue);
    } catch (Throwable e) {
      propagateIfFatal(e);
      throw new TestAbortedException(
        "Couldn't declare queue " + queue + ": " + e.getMessage(), e);
    }
    if (result.getExitCode() != 0) {
      throw new TestAbortedException("Couldn't declare queue " + queue + ": " + result);
    }
  }

  String host() {
    return container.getHost();
  }

  int port() {
    return container.getMappedPort(RABBIT_PORT);
  }

  // mostly waiting for https://github.com/testcontainers/testcontainers-java/issues/3537
  static final class RabbitMQContainer extends GenericContainer<RabbitMQContainer> {
    RabbitMQContainer() {
      super(parse("ghcr.io/openzipkin/rabbitmq-management-alpine:latest"));
      if ("true".equals(System.getProperty("docker.skip"))) {
        throw new TestAbortedException("${docker.skip} == true");
      }
      withExposedPorts(RABBIT_PORT); // rabbit's image doesn't expose any port
      waitStrategy = Wait.forLogMessage(".*Server startup complete.*", 1);
      withStartupTimeout(Duration.ofSeconds(60));
    }
  }
}
