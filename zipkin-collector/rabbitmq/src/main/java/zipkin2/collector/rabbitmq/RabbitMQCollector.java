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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

/** This collector consumes encoded binary messages from a RabbitMQ queue. */
public final class RabbitMQCollector extends CollectorComponent {
  static final Callback<Void> NOOP = new Callback<Void>() {
    @Override public void onSuccess(Void value) {
    }

    @Override public void onError(Throwable t) {
    }
  };

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a RabbitMQ queue. */
  public static final class Builder extends CollectorComponent.Builder {
    Collector.Builder delegate = Collector.newBuilder(RabbitMQCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String queue = "zipkin";
    ConnectionFactory connectionFactory = new ConnectionFactory();
    Address[] addresses;
    int concurrency = 1;

    @Override
    public Builder storage(StorageComponent storage) {
      this.delegate.storage(storage);
      return this;
    }

    @Override
    public Builder sampler(CollectorSampler sampler) {
      this.delegate.sampler(sampler);
      return this;
    }

    @Override
    public Builder metrics(CollectorMetrics metrics) {
      if (metrics == null) throw new NullPointerException("metrics == null");
      this.metrics = metrics.forTransport("rabbitmq");
      this.delegate.metrics(this.metrics);
      return this;
    }

    public Builder addresses(List<String> addresses) {
      this.addresses = convertAddresses(addresses);
      return this;
    }

    public Builder concurrency(int concurrency) {
      this.concurrency = concurrency;
      return this;
    }

    public Builder connectionFactory(ConnectionFactory connectionFactory) {
      if (connectionFactory == null) throw new NullPointerException("connectionFactory == null");
      this.connectionFactory = connectionFactory;
      return this;
    }

    /** Queue zipkin spans will be consumed from. Defaults to "zipkin-spans". */
    public Builder queue(String queue) {
      if (queue == null) throw new NullPointerException("queue == null");
      this.queue = queue;
      return this;
    }

    @Override
    public RabbitMQCollector build() {
      return new RabbitMQCollector(this);
    }
  }

  final String queue;
  final LazyInit connection;

  RabbitMQCollector(Builder builder) {
    this.queue = builder.queue;
    this.connection = new LazyInit(builder);
  }

  @Override
  public RabbitMQCollector start() {
    connection.get();
    return this;
  }

  @Override
  public CheckResult check() {
    try {
      start();
      CheckResult failure = connection.failure.get();
      if (failure != null) return failure;
      return CheckResult.OK;
    } catch (Throwable e) {
      Call.propagateIfFatal(e);
      return CheckResult.failed(e);
    }
  }

  @Override
  public void close() throws IOException {
    connection.close();
  }

  @Override public final String toString() {
    return "RabbitMQCollector{addresses="
      + Arrays.toString(connection.builder.addresses)
      + ", queue="
      + queue
      + "}";
  }

  /** Lazy creates a connection and a queue before starting consumers */
  static final class LazyInit {
    final Builder builder;
    final AtomicReference<CheckResult> failure = new AtomicReference<>();
    volatile Connection connection;

    // TODO: bad idea to lazy reference properties from a mutable builder
    // copy them here and then pass this to the KafkaCollectorWorker ctor instead
    LazyInit(Builder builder) {
      this.builder = builder;
    }

    Connection get() {
      if (connection == null) {
        synchronized (this) {
          if (connection == null) {
            connection = compute();
          }
        }
      }
      return connection;
    }

    void close() throws IOException {
      Connection maybeConnection = connection;
      if (maybeConnection != null) maybeConnection.close();
    }

    Connection compute() {
      Connection connection;
      try {
        connection =
          (builder.addresses == null)
            ? builder.connectionFactory.newConnection()
            : builder.connectionFactory.newConnection(builder.addresses);
        declareQueueIfMissing(connection);
      } catch (IOException e) {
        throw new UncheckedIOException(
          "Unable to establish connection to RabbitMQ server: " + e.getMessage(), e);
      } catch (TimeoutException e) {
        throw new RuntimeException(
          "Timeout establishing connection to RabbitMQ server: " + e.getMessage(), e);
      }
      Collector collector = builder.delegate.build();
      CollectorMetrics metrics = builder.metrics;

      for (int i = 0; i < builder.concurrency; i++) {
        String consumerTag = "zipkin-rabbitmq." + i;
        try {
          // this sets up a channel for each consumer thread.
          // We don't track channels, as the connection will close its channels implicitly
          Channel channel = connection.createChannel();
          RabbitMQSpanConsumer consumer = new RabbitMQSpanConsumer(channel, collector, metrics);
          channel.basicConsume(builder.queue, true, consumerTag, consumer);
        } catch (IOException e) {
          throw new IllegalStateException("Failed to start RabbitMQ consumer " + consumerTag, e);
        }
      }
      return connection;
    }

    private void declareQueueIfMissing(Connection connection) throws IOException, TimeoutException {
      Channel channel = connection.createChannel();
      try {
        // check if queue already exists
        channel.queueDeclarePassive(builder.queue);
        channel.close();
      } catch (IOException maybeQueueDoesNotExist) {
        Throwable cause = maybeQueueDoesNotExist.getCause();
        if (cause != null && cause.getMessage().contains("NOT_FOUND")) {
          channel = connection.createChannel();
          channel.queueDeclare(builder.queue, true, false, false, null);
          channel.close();
        } else {
          throw maybeQueueDoesNotExist;
        }
      }
    }
  }

  /**
   * Consumes spans from messages on a RabbitMQ queue. Malformed messages will be discarded. Errors
   * in the storage component will similarly be ignored, with no retry of the message.
   */
  static class RabbitMQSpanConsumer extends DefaultConsumer {
    final Collector collector;
    final CollectorMetrics metrics;

    RabbitMQSpanConsumer(Channel channel, Collector collector, CollectorMetrics metrics) {
      super(channel);
      this.collector = collector;
      this.metrics = metrics;
    }

    @Override
    public void handleDelivery(String tag, Envelope envelope, BasicProperties props, byte[] body) {
      metrics.incrementMessages();
      metrics.incrementBytes(body.length);

      if (body.length == 0) return; // lenient on empty messages

      collector.acceptSpans(body, NOOP);
    }
  }

  static Address[] convertAddresses(List<String> addresses) {
    Address[] addressArray = new Address[addresses.size()];
    for (int i = 0; i < addresses.size(); i++) {
      String[] splitAddress = addresses.get(i).split(":", 100);
      String host = splitAddress[0];
      int port = -1;
      try {
        if (splitAddress.length == 2) port = Integer.parseInt(splitAddress[1]);
      } catch (NumberFormatException ignore) {
        // EmptyCatch ignored
      }
      addressArray[i] = (port > 0) ? new Address(host, port) : new Address(host);
    }
    return addressArray;
  }
}
