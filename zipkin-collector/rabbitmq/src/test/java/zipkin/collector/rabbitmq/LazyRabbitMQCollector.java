/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.collector.rabbitmq;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.HostPortWaitStrategy;
import zipkin.Component;
import zipkin.collector.InMemoryCollectorMetrics;
import zipkin.internal.LazyCloseable;
import zipkin.storage.InMemoryStorage;

class LazyRabbitMQCollector extends LazyCloseable<RabbitMQCollector>
  implements TestRule {
  static final int RABBIT_PORT = 5672;

  final InMemoryStorage storage = new InMemoryStorage();
  final InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
  final InMemoryCollectorMetrics rabbitmqMetrics = metrics.forTransport("rabbitmq");

  final String image;
  GenericContainer container;

  LazyRabbitMQCollector(String image) {
    this.image = image;
  }

  @Override protected RabbitMQCollector compute() {
    try {
      container = new GenericContainer(image)
        .withExposedPorts(RABBIT_PORT)
        .waitingFor(new HostPortWaitStrategy());
      container.start();
      System.out.println("Starting docker image " + image);
    } catch (RuntimeException e) {
      // Ignore
    }

    RabbitMQCollector result = computeCollectorBuilder().build();
    result.start();

    Component.CheckResult check = result.check();
    if (!check.ok) {
      throw new AssumptionViolatedException(check.exception.getMessage(), check.exception);
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
      return String.format("%s:%d",
        container.getContainerIpAddress(),
        container.getMappedPort(RABBIT_PORT)
      );
    } else {
      // Use localhost if we failed to start a container (i.e. Docker is not available)
      return "localhost:" + RABBIT_PORT;
    }
  }

  void publish(byte[] message) throws IOException, TimeoutException {
    RabbitMQCollector thing = get();
    Channel channel = get().connection.get().createChannel();
    try {
      channel.basicPublish("", thing.queue, null, message);
    } finally {
      channel.close();
    }
  }

  @Override public void close() throws IOException {
    try {
      RabbitMQCollector collector = maybeNull();
      if (collector != null) collector.close();
    } finally {
      if (container != null) {
        System.out.println("Stopping docker image " + image);
        container.stop();
      }
    }
  }

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        get();
        try {
          base.evaluate();
        } finally {
          close();
        }
      }
    };
  }
}
