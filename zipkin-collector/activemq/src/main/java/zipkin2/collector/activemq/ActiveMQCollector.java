/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import org.apache.activemq.ActiveMQConnectionFactory;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

import javax.jms.*;
import java.io.Closeable;
import java.io.IOException;
import java.lang.IllegalStateException;
import java.util.concurrent.atomic.AtomicReference;


/** This collector consumes encoded binary messages from a RabbitMQ queue. */
public final class ActiveMQCollector extends CollectorComponent {

  static final Callback<Void> NOOP =
    new Callback<Void>() {
      @Override
      public void onSuccess(Void value) {}

      @Override
      public void onError(Throwable t) {}
    };

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a RabbitMQ queue. */
  public static final class Builder extends CollectorComponent.Builder {
    Collector.Builder delegate = Collector.newBuilder(ActiveMQCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String queue = "zipkin";
    String addresses;
    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();


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
      if (metrics == null) {
        throw new NullPointerException("metrics == null");
      }
      this.delegate.metrics(this.metrics);
      return this;
    }

    public Builder addresses(String addresses) {
      this.addresses = addresses;
      return this;
    }

    public Builder connectionFactory(ActiveMQConnectionFactory connectionFactory) {
      if (connectionFactory == null) {
        throw new NullPointerException("connectionFactory == null");
      }
      return this;
    }

    /**
     * Queue zipkin spans will be consumed from. Defaults to "zipkin-spans".
     */
    public Builder queue(String queue) {
      if (queue == null) {
        throw new NullPointerException("queue == null");
      }
      return this;
    }

    public Builder username(String username) {
      ((ActiveMQConnectionFactory)connectionFactory).setUserName(username);
      return this;
    }

    /** The password to use when connecting to the broker. Defaults to "guest" */
    public Builder password(String password) {
      ((ActiveMQConnectionFactory)connectionFactory).setPassword(password);
      return this;
    }

    @Override
    public ActiveMQCollector build() {
      return new ActiveMQCollector(this);
    }
  }

  final String queue;
  final LazyInit connection;

  ActiveMQCollector(Builder builder) {
    this.queue = builder.queue;
    this.connection = new LazyInit(builder);
  }

  @Override
  public ActiveMQCollector start() {
    connection.get();
    return this;
  }

  @Override
  public CheckResult check() {
    try {
      CheckResult failure = connection.failure.get();
      if (failure != null) return failure;
      return CheckResult.OK;
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
  }

  @Override
  public void close() throws IOException {
    connection.close();
  }

  /** Lazy creates a connection and a queue before starting consumers */
  static final class LazyInit  {
    final Builder builder;
    final AtomicReference<CheckResult> failure = new AtomicReference<>();
    Connection connection;

    LazyInit(Builder builder) {
      this.builder = builder;
    }

    protected Connection  compute() {
      try {
        builder.connectionFactory.setBrokerURL("failover:("+builder.addresses+")?initialReconnectDelay=100");
        connection = builder.connectionFactory.createConnection();
        Session session=connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        connection.start();
        Destination destination = session.createQueue(builder.queue);
        MessageConsumer messageConsumer = session.createConsumer(destination);
        Collector collector = builder.delegate.build();
        CollectorMetrics metrics = builder.metrics;
        messageConsumer.setMessageListener(new ActiveMQSpanConsumerMessageListener(collector,metrics));
      }catch (Exception e){
        throw new IllegalStateException("Unable to establish connection to ActiveMQ server", e);
      }
      return connection;
    }

    void close() throws IOException {
      Connection maybeConnection = connection;
      if (maybeConnection != null) {
        try {
          maybeConnection.close();
        }catch (Exception e){
          throw new IOException(e);
        }

      }
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


  }


  /**
   * Consumes spans from messages on a ActiveMQ queue. Malformed messages will be discarded. Errors
   * in the storage component will similarly be ignored, with no retry of the message.
   */
  static class ActiveMQSpanConsumerMessageListener implements MessageListener {
    final Collector collector;
    final CollectorMetrics metrics;

    ActiveMQSpanConsumerMessageListener(Collector collector, CollectorMetrics metrics) {
      this.collector = collector;
      this.metrics = metrics;
    }

    @Override
    public void onMessage(Message message) {
      metrics.incrementMessages();
      if(message instanceof BytesMessage) {
        try {
          byte [] data = new byte[(int)((BytesMessage)message).getBodyLength()];
          ((BytesMessage)message).readBytes(data);
          this.collector.acceptSpans(data, NOOP);
        }catch (Exception e){
        }
      }
    }


  }

}
