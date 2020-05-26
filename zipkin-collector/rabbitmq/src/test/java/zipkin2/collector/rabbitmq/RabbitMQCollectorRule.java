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
import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import zipkin2.CheckResult;

import static java.util.Arrays.asList;
import static zipkin2.Call.propagateIfFatal;

/** This should be used as a {@link ClassRule} as it takes a very long time to start-up. */
class RabbitMQCollectorRule extends ExternalResource {
  static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQCollectorRule.class);
  static final String IMAGE = "rabbitmq:3.7-management-alpine";
  static final String QUEUE = "zipkin-test1";
  static final int RABBIT_PORT = 5672;

  static final class RabbitMQContainer extends GenericContainer<RabbitMQContainer> {
    RabbitMQContainer(String image) {
      super(image);
      addExposedPorts(RABBIT_PORT);
      this.waitStrategy = Wait.forLogMessage(".*Server startup complete.*", 1)
          .withStartupTimeout(Duration.ofSeconds(60));
    }
  }

  RabbitMQContainer container;

  @Override protected void before() {
    if ("true".equals(System.getProperty("docker.skip"))) {
      throw new AssumptionViolatedException("Skipping startup of docker " + IMAGE);
    }

    try {
      LOGGER.info("Starting docker image " + IMAGE);
      container = new RabbitMQContainer(IMAGE);
      container.start();
    } catch (Throwable e) {
      throw new AssumptionViolatedException(
          "Couldn't start docker image " + IMAGE + ": " + e.getMessage(), e);
    }

    declareQueue(QUEUE);
  }

  RabbitMQCollector tryToInitializeCollector(RabbitMQCollector.Builder collectorBuilder) {
    RabbitMQCollector result = collectorBuilder.build();

    CheckResult check;
    try {
      check = result.check();
    } catch (Throwable e) {
      propagateIfFatal(e);
      throw new AssertionError("collector.check shouldn't propagate errors", e);
    }

    if (!check.ok()) {
      throw new AssumptionViolatedException(
          "Couldn't connect to docker container " + container + ": " +
              check.error().getMessage(), check.error());
    }

    return result;
  }

  RabbitMQCollector.Builder newCollectorBuilder() {
    return RabbitMQCollector.builder().queue(QUEUE).addresses(
        asList(container.getContainerIpAddress() + ":" + container.getMappedPort(RABBIT_PORT)));
  }

  void declareQueue(String queue) {
    ExecResult result;
    try {
      result = container.execInContainer("rabbitmqadmin", "declare", "queue", "name=" + queue);
    } catch (Throwable e) {
      propagateIfFatal(e);
      throw new AssumptionViolatedException(
          "Couldn't declare queue " + queue + ": " + e.getMessage(), e);
    }
    if (result.getExitCode() != 0) {
      throw new AssumptionViolatedException("Couldn't declare queue " + queue + ": " + result);
    }
  }

  @Override protected void after() {
    if (container != null) {
      LOGGER.info("Stopping docker container " + container);
      container.stop();
    }
  }
}
