/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.activemq;

import java.time.Duration;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.testcontainers.utility.DockerImageName.parse;

class ActiveMQExtension implements BeforeAllCallback, AfterAllCallback {
  static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQExtension.class);
  static final int ACTIVEMQ_PORT = 61616;

  ActiveMQContainer container = new ActiveMQContainer();

  @Override public void beforeAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.start();
    LOGGER.info("Using brokerURL " + brokerURL());
  }

  @Override public void afterAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.stop();
  }

  ActiveMQCollector.Builder newCollectorBuilder(String queue) {
    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
    connectionFactory.setBrokerURL(brokerURL());
    return ActiveMQCollector.builder().queue(queue).connectionFactory(connectionFactory);
  }

  String brokerURL() {
    return "failover:tcp://" + container.getHost() + ":" + container.getMappedPort(ACTIVEMQ_PORT);
  }

  // mostly waiting for https://github.com/testcontainers/testcontainers-java/issues/3537
  static final class ActiveMQContainer extends GenericContainer<ActiveMQContainer> {
    ActiveMQContainer() {
      super(parse("ghcr.io/openzipkin/zipkin-activemq:3.3.1"));
      withExposedPorts(ACTIVEMQ_PORT);
      waitStrategy = Wait.forListeningPorts(ACTIVEMQ_PORT);
      withStartupTimeout(Duration.ofSeconds(60));
    }
  }
}
