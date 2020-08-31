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

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/** This should be used as a {@link ClassRule} as it takes a very long time to start-up. */
class KafkaCollectorRule extends ExternalResource {
  static final Logger LOGGER = LoggerFactory.getLogger(KafkaCollectorRule.class);
  static final String IMAGE = "openzipkin/zipkin-kafka:2.21.5";
  static final int KAFKA_PORT = 19092;
  static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:" + KAFKA_PORT;
  static final String KAFKA_TOPIC = "zipkin";

  static final class KafkaContainer extends FixedHostPortGenericContainer<KafkaContainer> {
    KafkaContainer(String image) {
      super(image);
      withFixedExposedPort(KAFKA_PORT, KAFKA_PORT);
      this.waitStrategy =
        new LogMessageWaitStrategy().withRegEx(".*INFO \\[KafkaServer id=0\\] started.*");
    }
  }

  KafkaContainer container;

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
    config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
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


  KafkaCollector.Builder newCollectorBuilder() {
    return KafkaCollector.builder().bootstrapServers(KAFKA_BOOTSTRAP_SERVERS);
  }

  @Override protected void after() {
    if (container != null) {
      LOGGER.info("Stopping docker container " + container);
      container.stop();
    }
  }
}
