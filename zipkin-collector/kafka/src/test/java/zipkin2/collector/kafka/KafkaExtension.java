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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
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

import static org.testcontainers.utility.DockerImageName.parse;

class KafkaExtension implements BeforeAllCallback, AfterAllCallback {
  static final Logger LOGGER = LoggerFactory.getLogger(KafkaExtension.class);
  static final int KAFKA_PORT = 19092;

  final KafkaContainer container = new KafkaContainer();

  @Override public void beforeAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.start();
    LOGGER.info("Using bootstrapServer " + bootstrapServer());
  }

  void prepareTopics(String topics, int partitions) {
    Properties config = new Properties();
    config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer());

    List<NewTopic> newTopics = new ArrayList<>();
    for (String topic : topics.split(",")) {
      if ("".equals(topic)) continue;
      newTopics.add(new NewTopic(topic, partitions, (short) 1));
    }

    try (AdminClient adminClient = AdminClient.create(config)) {
      adminClient.createTopics(newTopics).all().get();
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() != null && e.getCause() instanceof TopicExistsException) return;
      throw new TestAbortedException(
        "Topics could not be created " + newTopics + ": " + e.getMessage(), e);
    }
  }

  String bootstrapServer() {
    return container.getHost() + ":" + container.getMappedPort(KAFKA_PORT);
  }

  KafkaCollector.Builder newCollectorBuilder(String topic, int streams) {
    prepareTopics(topic, streams);
    return KafkaCollector.builder().bootstrapServers(bootstrapServer())
      .topic(topic)
      .groupId(topic + "_group")
      .streams(streams);
  }

  @Override public void afterAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }
    container.stop();
  }

  // mostly waiting for https://github.com/testcontainers/testcontainers-java/issues/3537
  static final class KafkaContainer extends GenericContainer<KafkaContainer> {
    KafkaContainer() {
      super(parse("ghcr.io/openzipkin/zipkin-kafka:2.23.2"));
      if ("true".equals(System.getProperty("docker.skip"))) {
        throw new TestAbortedException("${docker.skip} == true");
      }
      waitStrategy = Wait.forHealthcheck();
      // 19092 is for connections from the Docker host and needs to be used as a fixed port.
      // TODO: someone who knows Kafka well, make ^^ comment better!
      addFixedExposedPort(KAFKA_PORT, KAFKA_PORT, InternetProtocol.TCP);
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }
  }
}
