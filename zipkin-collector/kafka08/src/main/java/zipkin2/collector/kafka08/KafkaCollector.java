/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.collector.kafka08;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ZookeeperConsumerConnector;
import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.collector.filter.SpanFilter;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.StorageComponent;

import static kafka.consumer.Consumer.createJavaConsumerConnector;
import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;

/**
 * This collector polls a Kafka topic for messages that contain TBinaryProtocol big-endian encoded
 * lists of spans. These spans are pushed to a {@link SpanConsumer#accept span consumer}.
 *
 * <p>This collector remains a Kafka 0.8.x consumer, while Zipkin systems update to 0.9+.
 */
public final class KafkaCollector extends CollectorComponent {

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a Kafka topic. */
  public static final class Builder extends CollectorComponent.Builder {
    final Properties properties = new Properties();
    Collector.Builder delegate = Collector.newBuilder(KafkaCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String topic = "zipkin";
    int streams = 1;

    @Override
    public Builder storage(StorageComponent storage) {
      delegate.storage(storage);
      return this;
    }

    @Override
    public Builder sampler(CollectorSampler sampler) {
      delegate.sampler(sampler);
      return this;
    }

    @Override
    public CollectorComponent.Builder filters(List<SpanFilter> filters) {
      delegate.filters(filters);
      return this;
    }

    @Override
    public Builder metrics(CollectorMetrics metrics) {
      if (metrics == null) throw new NullPointerException("metrics == null");
      this.metrics = metrics.forTransport("kafka");
      delegate.metrics(this.metrics);
      return this;
    }

    /** Topic zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder topic(String topic) {
      if (topic == null) throw new NullPointerException("topic == null");
      this.topic = topic;
      return this;
    }

    /** The zookeeper connect string, ex. 127.0.0.1:2181. No default */
    public Builder zookeeper(String zookeeper) {
      if (zookeeper == null) throw new NullPointerException("zookeeper == null");
      properties.put("zookeeper.connect", zookeeper);
      return this;
    }

    /** The consumer group this process is consuming on behalf of. Defaults to "zipkin" */
    public Builder groupId(String groupId) {
      if (groupId == null) throw new NullPointerException("groupId == null");
      properties.put(GROUP_ID_CONFIG, groupId);
      return this;
    }

    /** Count of threads/streams consuming the topic. Defaults to 1 */
    public Builder streams(int streams) {
      this.streams = streams;
      return this;
    }

    /** Maximum size of a message containing spans in bytes. Defaults to 1 MiB */
    public Builder maxMessageSize(int bytes) {
      properties.put("fetch.message.max.bytes", String.valueOf(bytes));
      return this;
    }

    /**
     * By default, a consumer will be built from properties derived from builder defaults, as well
     * "auto.offset.reset" -> "smallest". Any properties set here will override the consumer config.
     *
     * <p>For example: Only consume spans since you connected by setting the below.
     *
     * <pre>{@code
     * Map<String, String> overrides = new LinkedHashMap<>();
     * overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "largest");
     * builder.overrides(overrides);
     * }</pre>
     *
     * @see org.apache.kafka.clients.consumer.ConsumerConfig
     */
    public final Builder overrides(Map<String, ?> overrides) {
      if (overrides == null) throw new NullPointerException("overrides == null");
      properties.putAll(overrides);
      return this;
    }

    @Override
    public KafkaCollector build() {
      return new KafkaCollector(this);
    }

    Builder() {
      // Settings below correspond to "Old Consumer Configs"
      // http://kafka.apache.org/documentation.html
      properties.put(GROUP_ID_CONFIG, "zipkin");
      properties.put("fetch.message.max.bytes", String.valueOf(1024 * 1024));
      // Same default as zipkin-scala, and keeps tests from hanging
      properties.put(AUTO_OFFSET_RESET_CONFIG, "smallest");
    }
  }

  final LazyConnector connector;
  final LazyStreams streams;

  KafkaCollector(Builder builder) {
    connector = new LazyConnector(builder);
    streams = new LazyStreams(builder, connector);
  }

  @Override
  public KafkaCollector start() {
    connector.get();
    streams.get();
    return this;
  }

  @Override
  public CheckResult check() {
    try {
      connector.get(); // make sure the connector didn't throw
      CheckResult failure = streams.failure.get(); // check the streams didn't quit
      if (failure != null) return failure;
      return CheckResult.OK;
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
  }

  static final class LazyConnector {

    final ConsumerConfig config;
    volatile ZookeeperConsumerConnector connector;

    LazyConnector(Builder builder) {
      this.config = new ConsumerConfig(builder.properties);
    }

    ZookeeperConsumerConnector get() {
      if (connector == null) {
        synchronized (this) {
          if (connector == null) {
            connector = (ZookeeperConsumerConnector) createJavaConsumerConnector(config);
          }
        }
      }
      return connector;
    }

    void close() {
      ZookeeperConsumerConnector maybeConnector = connector;
      if (maybeConnector == null) return;
      maybeConnector.shutdown();
    }
  }

  @Override
  public void close() {
    streams.close();
    connector.close();
  }

  static final class LazyStreams {
    final int streams;
    final String topic;
    final Collector collector;
    final CollectorMetrics metrics;
    final LazyConnector connector;
    final AtomicReference<CheckResult> failure = new AtomicReference<>();
    volatile ExecutorService pool;

    LazyStreams(Builder builder, LazyConnector connector) {
      this.streams = builder.streams;
      this.topic = builder.topic;
      this.collector = builder.delegate.build();
      this.metrics = builder.metrics;
      this.connector = connector;
    }

    ExecutorService get() {
      if (pool == null) {
        synchronized (this) {
          if (pool == null) {
            pool = compute();
          }
        }
      }
      return pool;
    }

    void close() {
      ExecutorService maybePool = pool;
      if (maybePool == null) return;
      maybePool.shutdownNow();
      try {
        maybePool.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // at least we tried
      }
    }

    ExecutorService compute() {
      ExecutorService pool =
          streams == 1
              ? Executors.newSingleThreadExecutor()
              : Executors.newFixedThreadPool(streams);

      Map<String, Integer> topicCountMap = new LinkedHashMap<>(1);
      topicCountMap.put(topic, streams);

      for (KafkaStream<byte[], byte[]> stream :
          connector.get().createMessageStreams(topicCountMap).get(topic)) {
        pool.execute(guardFailures(new KafkaStreamProcessor(stream, collector, metrics)));
      }
      return pool;
    }

    Runnable guardFailures(final Runnable delegate) {
      return new Runnable() {
        @Override
        public void run() {
          try {
            delegate.run();
          } catch (RuntimeException e) {
            failure.set(CheckResult.failed(e));
          }
        }
      };
    }
  }
}
