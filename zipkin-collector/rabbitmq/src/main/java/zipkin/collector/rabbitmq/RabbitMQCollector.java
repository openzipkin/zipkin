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

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.collector.Collector;
import zipkin.collector.CollectorComponent;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.LazyCloseable;
import zipkin.storage.StorageComponent;

import static zipkin.internal.Util.checkNotNull;

/**
 * This collector consumes encoded binary messages from a RabbitMQ queue.
 */
public final class RabbitMQCollector implements CollectorComponent {
  private static final Logger LOG = LoggerFactory.getLogger(RabbitMQCollector.class);

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a RabbitMQ queue. */
  public static final class Builder implements CollectorComponent.Builder {
    Collector.Builder delegate = Collector.builder(RabbitMQCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String queue = "zipkin";
    ConnectionFactory connectionFactory;
    List<String> addresses;
    int concurrency = 1;

    @Override public Builder storage(StorageComponent storage) {
      this.delegate.storage(storage);
      return this;
    }

    @Override public Builder sampler(CollectorSampler sampler) {
      this.delegate.sampler(sampler);
      return this;
    }

    @Override public Builder metrics(CollectorMetrics metrics) {
      this.metrics = checkNotNull(metrics, "metrics").forTransport("rabbitmq");
      this.delegate.metrics(this.metrics);
      return this;
    }

    public Builder addresses(List<String> addresses) {
      this.addresses = addresses;
      return this;
    }

    public Builder concurrency(int concurrency) {
      this.concurrency = concurrency;
      return this;
    }

    public Builder connectionFactory(ConnectionFactory connectionFactory) {
      this.connectionFactory = checkNotNull(connectionFactory, "connectionFactory");
      return this;
    }

    /**
     * Queue zipkin spans will be consumed from. Defaults to "zipkin-spans".
     */
    public Builder queue(String queue) {
      this.queue = checkNotNull(queue, "queue");
      return this;
    }

    @Override public RabbitMQCollector build() {
      return new RabbitMQCollector(this);
    }
  }

  private final LazyRabbitWorkers rabbitWorkers;

  RabbitMQCollector(Builder builder) {
    this.rabbitWorkers = new LazyRabbitWorkers(builder);
  }

  @Override
  public RabbitMQCollector start() {
    this.rabbitWorkers.get();
    return this;
  }

  @Override
  public CheckResult check() {
    try {
      CheckResult failure = this.rabbitWorkers.failure.get();
      if (failure != null) return failure;
      return CheckResult.OK;
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
  }

  @Override
  public void close() throws IOException {
    this.rabbitWorkers.close();
  }

  static final class LazyRabbitWorkers extends LazyCloseable<ExecutorService> {

    final Builder builder;
    final int concurrency;
    final AtomicReference<CheckResult> failure = new AtomicReference<>();
    private Connection connection;

    LazyRabbitWorkers(Builder builder) {
      this.builder = builder;
      this.concurrency = builder.concurrency;
    }

    @Override
    protected ExecutorService compute() {
      ExecutorService pool = concurrency == 1
        ? Executors.newSingleThreadExecutor()
        : Executors.newFixedThreadPool(concurrency);

      try {
        this.connection =
          this.builder.connectionFactory.newConnection(convertAddresses(this.builder.addresses));
      } catch (IOException | TimeoutException e) {
        throw new RabbitCollectorStartupException(
          "Unable to establish connection to RabbitMQ server", e);
      }

      for (int i = 0; i < concurrency; i++) {
        final RabbitMQWorker worker =
          new RabbitMQWorker(this.builder, this.connection, RabbitMQWorker.class.getName() + i);
        pool.execute(guardFailures(worker));
      }

      return pool;
    }

    Runnable guardFailures(final Runnable delegate) {
      return () -> {
        try {
          delegate.run();
        } catch (RuntimeException e) {
          LOG.error("RabbitMQ collector consumer exited with exception", e);
          this.failure.set(CheckResult.failed(e));
        }
      };
    }

    @Override
    public void close() {
      ExecutorService maybeNull = maybeNull();
      if (maybeNull != null) {
        maybeNull.shutdownNow();
        try {
          maybeNull.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          // at least we tried
        } finally {
          try {
            this.connection.close();
          } catch (IOException ignore) {
            LOG.info("Failed to close RabbitMQ connection when stopping the collector.");
          }
        }
      }
    }

    static class RabbitCollectorStartupException extends RuntimeException {
      RabbitCollectorStartupException(String message, Throwable cause) {
        super(message, cause);
      }
    }
  }

  static Address[] convertAddresses(List<String> addresses) {
    Address[] addressArray = new Address[addresses.size()];
    for (int i = 0; i < addresses.size(); i++) {
      String[] splitAddress = addresses.get(i).split(":");
      String host = splitAddress[0];
      Integer port = null;
      try {
        if (splitAddress.length == 2) port = Integer.parseInt(splitAddress[1]);
      } catch (NumberFormatException ignore) { }
      addressArray[i] = (port != null) ? new Address(host, port) : new Address(host);
    }
    return addressArray;
  }
}
