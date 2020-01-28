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

import java.io.IOException;
import java.io.UncheckedIOException;
import javax.jms.JMSException;
import org.apache.activemq.ActiveMQConnectionFactory;
import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

/** This collector consumes encoded binary messages from a ActiveMQ queue. */
public final class ActiveMQCollector extends CollectorComponent {
  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a ActiveMQ queue. */
  public static final class Builder extends CollectorComponent.Builder {
    Collector.Builder delegate = Collector.newBuilder(ActiveMQCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    ActiveMQConnectionFactory connectionFactory;
    String queue = "zipkin";
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
      if (metrics == null) throw new NullPointerException("metrics == null");
      this.metrics = metrics.forTransport("activemq");
      this.delegate.metrics(this.metrics);
      return this;
    }

    public Builder connectionFactory(ActiveMQConnectionFactory connectionFactory) {
      if (connectionFactory == null) throw new NullPointerException("connectionFactory == null");
      this.connectionFactory = connectionFactory;
      return this;
    }

    /** Queue zipkin spans will be consumed from. Defaults to "zipkin". */
    public Builder queue(String queue) {
      if (queue == null) throw new NullPointerException("queue == null");
      this.queue = queue;
      return this;
    }

    /** Count of concurrent message listeners on the queue. Defaults to 1 */
    public Builder concurrency(int concurrency) {
      if (concurrency < 1) throw new IllegalArgumentException("concurrency < 1");
      this.concurrency = concurrency;
      return this;
    }

    @Override public ActiveMQCollector build() {
      if (connectionFactory == null) throw new NullPointerException("connectionFactory == null");
      return new ActiveMQCollector(this);
    }
  }

  final String queue;
  final LazyInit lazyInit;

  ActiveMQCollector(Builder builder) {
    this.queue = builder.queue;
    this.lazyInit = new LazyInit(builder);
  }

  @Override public ActiveMQCollector start() {
    lazyInit.init();
    return this;
  }

  @Override public CheckResult check() {
    if (lazyInit.result == null) {
      return CheckResult.failed(new IllegalStateException("Collector not yet started"));
    }
    return lazyInit.result.checkResult;
  }

  @Override public void close() throws IOException {
    lazyInit.close();
  }

  @Override public final String toString() {
    return "ActiveMQCollector{"
      + "brokerURL=" + lazyInit.connectionFactory.getBrokerURL()
      + ", queue=" + lazyInit.queue
      + "}";
  }

  static RuntimeException uncheckedException(String prefix, JMSException e) {
    Exception cause = e.getLinkedException();
    if (cause instanceof IOException) {
      return new UncheckedIOException(prefix + message(cause), (IOException) cause);
    }
    return new RuntimeException(prefix + message(e), e);
  }

  static String message(Exception cause) {
    return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
  }
}
