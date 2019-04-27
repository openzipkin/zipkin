/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.collector.rabbitmq;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import org.junit.AssumptionViolatedException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import zipkin2.CheckResult;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.InMemoryStorage;

class RabbitMQCollectorRule extends ExternalResource {
  static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQCollectorRule.class);
  static final int RABBIT_PORT = 5672;

  final InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  final InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  final InMemoryCollectorMetrics rabbitmqMetrics = metrics.forTransport("rabbitmq");

  final String image;
  GenericContainer container;
  RabbitMQCollector collector;

  RabbitMQCollectorRule(String image) {
    this.image = image;
  }

  @Override
  protected void before() {
    if (!"true".equals(System.getProperty("docker.skip"))) {
      try {
        LOGGER.info("Starting docker image " + image);
        container = new GenericContainer(image).withExposedPorts(RABBIT_PORT);
        container.start();
      } catch (RuntimeException e) {
        LOGGER.warn("Couldn't start docker image " + image + ": " + e.getMessage(), e);
      }
    } else {
      LOGGER.info("Skipping startup of docker " + image);
    }

    try {
      this.collector = tryToInitializeCollector();
    } catch (RuntimeException | Error e) {
      if (container == null) throw e;
      LOGGER.warn("Couldn't connect to docker image " + image + ": " + e.getMessage(), e);
      container.stop();
      container = null; // try with local connection instead
      this.collector = tryToInitializeCollector();
    }
  }

  RabbitMQCollector tryToInitializeCollector() {
    RabbitMQCollector result = computeCollectorBuilder().build();
    try {
      result.start();
    } catch (RuntimeException e) {
      throw new AssumptionViolatedException(e.getMessage(), e);
    }

    CheckResult check = result.check();
    if (!check.ok()) {
      throw new AssumptionViolatedException(check.error().getMessage(), check.error());
    }
    return result;
  }

  RabbitMQCollector.Builder computeCollectorBuilder() {
    return RabbitMQCollector.builder()
        .storage(storage)
        .metrics(metrics)
        .queue("zipkin-test")
        .addresses(Arrays.asList(address()));
  }

  String address() {
    if (container != null && container.isRunning()) {
      return String.format(
          "%s:%d", container.getContainerIpAddress(), container.getMappedPort(RABBIT_PORT));
    } else {
      // Use localhost if we failed to start a container (i.e. Docker is not available)
      return "localhost:" + RABBIT_PORT;
    }
  }

  void publish(byte[] message) throws IOException, TimeoutException {
    Channel channel = collector.connection.get().createChannel();
    try {
      channel.basicPublish("", collector.queue, null, message);
    } finally {
      channel.close();
    }
  }

  @Override
  protected void after() {
    try {
      if (collector != null) collector.close();
    } catch (IOException e) {
      LOGGER.warn("error closing collector " + e.getMessage(), e);
    } finally {
      if (container != null) {
        LOGGER.info("Stopping docker image " + image);
        container.stop();
      }
    }
  }
}
