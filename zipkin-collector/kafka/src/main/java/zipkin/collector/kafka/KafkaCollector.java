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
package zipkin.collector.kafka;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ZookeeperConsumerConnector;
import zipkin.collector.Collector;
import zipkin.collector.CollectorComponent;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.internal.LazyCloseable;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.StorageComponent;

import static kafka.consumer.Consumer.createJavaConsumerConnector;
import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static zipkin.internal.Util.checkNotNull;

/**
 * This collector polls a Kafka topic for messages that contain TBinaryProtocol big-endian encoded
 * lists of spans. These spans are pushed to a {@link AsyncSpanConsumer#accept span consumer}.
 *
 * <p>This collector remains a Kafka 0.8.x consumer, while Zipkin systems update to 0.9+.
 */
public final class KafkaCollector implements CollectorComponent {

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a Kafka topic. */
  public static final class Builder implements CollectorComponent.Builder {
    final Properties properties = new Properties();
    Collector.Builder delegate = Collector.builder(KafkaCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String topic = "zipkin";
    int streams = 1;

    @Override public Builder storage(StorageComponent storage) {
      delegate.storage(storage);
      return this;
    }

    @Override public Builder sampler(CollectorSampler sampler) {
      delegate.sampler(sampler);
      return this;
    }

    @Override public Builder metrics(CollectorMetrics metrics) {
      this.metrics = checkNotNull(metrics, "metrics").forTransport("kafka");
      delegate.metrics(this.metrics);
      return this;
    }

    /** Topic zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder topic(String topic) {
      this.topic = checkNotNull(topic, "topic");
      return this;
    }

    /** The zookeeper connect string, ex. 127.0.0.1:2181. No default */
    public Builder zookeeper(String zookeeper) {
      properties.put("zookeeper.connect", checkNotNull(zookeeper, "zookeeper"));
      return this;
    }

    /** The consumer group this process is consuming on behalf of. Defaults to "zipkin" */
    public Builder groupId(String groupId) {
      properties.put(GROUP_ID_CONFIG, checkNotNull(groupId, "groupId"));
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
     * By default, a consumer will be built from properties derived from builder defaults,
     * as well "auto.offset.reset" -> "smallest". Any properties set here will override the
     * consumer config.
     *
     * <p>For example: Only consume spans since you connected by setting the below.
     * <pre>{@code
     * Map<String, String> overrides = new LinkedHashMap<>();
     * overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "largest");
     * builder.overrides(overrides);
     * }</pre>
     *
     * @see org.apache.kafka.clients.consumer.ConsumerConfig
     */
    public final Builder overrides(Map<String, ?> overrides) {
      properties.putAll(checkNotNull(overrides, "overrides"));
      return this;
    }

    @Override public KafkaCollector build() {
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

  @Override public KafkaCollector start() {
    connector.get();
    streams.get();
    return this;
  }

  @Override public CheckResult check() {
    try {
      connector.get(); // make sure the connector didn't throw
      CheckResult failure = streams.failure.get(); // check the streams didn't quit
      if (failure != null) return failure;
      return CheckResult.OK;
    } catch (RuntimeException e) {
      return CheckResult.failed(e);
    }
  }

  @Override
  public void close() throws IOException {
    streams.close();
    connector.close();
  }

  static final class LazyConnector extends LazyCloseable<ZookeeperConsumerConnector> {

    final ConsumerConfig config;

    LazyConnector(Builder builder) {
      this.config = new ConsumerConfig(builder.properties);
    }

    @Override protected ZookeeperConsumerConnector compute() {
      return (ZookeeperConsumerConnector) createJavaConsumerConnector(config);
    }

    @Override
    public void close() {
      ZookeeperConsumerConnector maybeNull = maybeNull();
      if (maybeNull != null) maybeNull.shutdown();
    }
  }

  static final class LazyStreams extends LazyCloseable<ExecutorService> {
    final int streams;
    final String topic;
    final Collector collector;
    final CollectorMetrics metrics;
    final LazyCloseable<ZookeeperConsumerConnector> connector;
    final AtomicReference<CheckResult> failure = new AtomicReference<>();

    LazyStreams(Builder builder, LazyCloseable<ZookeeperConsumerConnector> connector) {
      this.streams = builder.streams;
      this.topic = builder.topic;
      this.collector = builder.delegate.build();
      this.metrics = builder.metrics;
      this.connector = connector;
    }

    @Override protected ExecutorService compute() {
      ExecutorService pool = streams == 1
          ? Executors.newSingleThreadExecutor()
          : Executors.newFixedThreadPool(streams);

      Map<String, Integer> topicCountMap = new LinkedHashMap<>(1);
      topicCountMap.put(topic, streams);

      for (KafkaStream<byte[], byte[]> stream : connector.get().createMessageStreams(topicCountMap)
          .get(topic)) {
        pool.execute(guardFailures(new KafkaStreamProcessor(stream, collector, metrics)));
      }
      return pool;
    }

    Runnable guardFailures(final Runnable delegate) {
      return new Runnable() {
        @Override public void run() {
          try {
            delegate.run();
          } catch (RuntimeException e) {
            failure.set(CheckResult.failed(e));
          }
        }
      };
    }

    @Override
    public void close() {
      ExecutorService maybeNull = maybeNull();
      if (maybeNull != null) maybeNull.shutdown();
    }
  }
}
