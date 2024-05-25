/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
    LOGGER.info("Using hostPort {}:{}", host(), port());
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
      result = container.execInContainer("amqp-declare-queue", "-q", queue);
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
      super(parse("ghcr.io/openzipkin/zipkin-rabbitmq:3.3.1"));
      withExposedPorts(RABBIT_PORT);
      waitStrategy = Wait.forLogMessage(".*Server startup complete.*", 1);
      withStartupTimeout(Duration.ofSeconds(60));
    }
  }
}
