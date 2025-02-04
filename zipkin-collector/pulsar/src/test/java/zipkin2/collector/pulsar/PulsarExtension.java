/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pulsar;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.time.Duration;

import static org.testcontainers.utility.DockerImageName.parse;

public class PulsarExtension implements BeforeAllCallback, AfterAllCallback {

  static final Logger LOGGER = LoggerFactory.getLogger(PulsarExtension.class);
  static final int BROKER_PORT = 6650;
  static final int BROKER_HTTP_PORT = 8080;

  final PulsarContainer container = new PulsarContainer();

  @Override public void beforeAll(ExtensionContext context) throws Exception {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.start();
    LOGGER.info("Using serviceUrl {}", serviceUrl());
  }

  String serviceUrl() {
    return "pulsar://" + container.getHost() + ":" + container.getMappedPort(BROKER_PORT);
  }

  @Override public void afterAll(ExtensionContext context) throws Exception {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.stop();
  }

  PulsarCollector.Builder newCollectorBuilder(String topic) {
    return PulsarCollector.builder()
        .topic(topic)
        .subscriptionName("zipkin-subscription")
        .serviceUrl(serviceUrl());
  }

  static final class PulsarContainer extends GenericContainer<PulsarContainer> {
    PulsarContainer() {
      super(parse("ghcr.io/openzipkin/zipkin-pulsar:3.4.3"));
      withExposedPorts(BROKER_PORT, BROKER_HTTP_PORT);
      String cmd = "/pulsar/bin/apply-config-from-env.py /pulsar/conf/standalone.conf " +
          "&& bin/pulsar standalone " +
          "--no-functions-worker -nss";
      withEnv("PULSAR_MEM", "-Xms512m -Xmx512m -XX:MaxDirectMemorySize=1g"); // limit memory usage
      waitStrategy = new HttpWaitStrategy()
          .forPort(BROKER_HTTP_PORT)
          .forStatusCode(200)
          .forPath("/admin/v2/clusters")
          .withStartupTimeout(Duration.ofSeconds(120));
      withCommand("/bin/bash", "-c", cmd);
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }
  }
}
