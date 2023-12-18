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
package zipkin2.collector.activemq;

import java.time.Duration;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
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
      super(parse("ghcr.io/openzipkin/zipkin-activemq:2.25.2"));
      if ("true".equals(System.getProperty("docker.skip"))) {
        throw new TestAbortedException("${docker.skip} == true");
      }
      withExposedPorts(ACTIVEMQ_PORT);
      waitStrategy = Wait.forListeningPorts(ACTIVEMQ_PORT);
      withStartupTimeout(Duration.ofSeconds(60));
    }
  }
}
