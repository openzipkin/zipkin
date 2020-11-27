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
package zipkin2.collector.kafka;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InternetProtocol;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.utility.DockerImageName.parse;

/**
 * This should be used as a {@link ClassRule} as it takes a very long time to start-up.
 */
class KafkaCollectorRule extends ExternalResource {
  static final Logger LOGGER = LoggerFactory.getLogger(KafkaCollectorRule.class);
  static final DockerImageName IMAGE = parse("ghcr.io/openzipkin/zipkin-kafka:2.23.0");
  static final int KAFKA_PORT = 19092;
  static final String KAFKA_TOPIC = "zipkin";
  KafkaContainer container;

  static final class KafkaContainer extends GenericContainer<KafkaContainer> {
    KafkaContainer(DockerImageName image) {
      super(image);
      // 19092 is for connections from the Docker host and needs to be used as a fixed port.
      // TODO: someone who knows Kafka well, make ^^ comment better!
      addFixedExposedPort(KAFKA_PORT, KAFKA_PORT, InternetProtocol.TCP);
      this.waitStrategy = Wait.forHealthcheck();
    }
  }

  @Override protected void before() {
    if ("true".equals(System.getProperty("docker.skip"))) {
      throw new AssumptionViolatedException("Skipping startup of docker " + IMAGE);
    }

    try {
      LOGGER.info("Starting docker image " + IMAGE);
      container = new KafkaContainer(IMAGE);
      container.start();
    } catch (Throwable e) {
      throw new AssumptionViolatedException(
        "Couldn't start docker image " + IMAGE + ": " + e.getMessage(), e);
    }

    prepareTopic(KAFKA_TOPIC, 1);
  }

  void prepareTopic(final String topic, final int partitions) {
    final Properties config = new Properties();
    config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
    AdminClient adminClient = AdminClient.create(config);
    try {
      adminClient.createTopics(
        Collections.singletonList(new NewTopic(topic, partitions, (short) 1))
      ).all().get();
    } catch (InterruptedException | ExecutionException e) {
      throw new AssumptionViolatedException(
        "Topic cannot be created " + topic + ": " + e.getMessage(), e);
    }
  }

  String bootstrapServers() {
    if (container != null && container.isRunning()) {
      return container.getContainerIpAddress() + ":" + container.getMappedPort(KAFKA_PORT);
    } else {
      return "127.0.0.1:" + KAFKA_PORT;
    }
  }

  KafkaCollector.Builder newCollectorBuilder() {
    return KafkaCollector.builder().bootstrapServers(bootstrapServers());
  }

  @Override protected void after() {
    if (container != null) {
      LOGGER.info("Stopping docker container " + container);
      container.stop();
    }
  }
}
