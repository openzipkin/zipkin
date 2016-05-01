/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.kafka;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kafka.consumer.ConsumerConfig;
import kafka.javaapi.consumer.ZookeeperConsumerConnector;
import zipkin.AsyncSpanConsumer;
import zipkin.Callback;
import zipkin.CollectorMetrics;
import zipkin.CollectorSampler;
import zipkin.Span;
import zipkin.StorageComponent;
import zipkin.internal.Lazy;

import static kafka.consumer.Consumer.createJavaConsumerConnector;
import static zipkin.internal.Util.checkNotNull;

/**
 * This collector polls a Kafka topic for messages that contain TBinaryProtocol big-endian encoded
 * lists of spans. These spans are pushed to a {@link AsyncSpanConsumer#accept span consumer}.
 *
 * <p>This collector remains a Kafka 0.8.x consumer, while Zipkin systems update to 0.9+.
 *
 */
public final class KafkaCollector implements AutoCloseable {

  /** Configuration including defaults needed to consume spans from a Kafka topic. */
  public static final class Builder {
    CollectorSampler sampler = CollectorSampler.ALWAYS_SAMPLE;
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String topic = "zipkin";
    String zookeeper;
    String groupId = "zipkin";
    int streams = 1;
    int maxMessageSize = 1024 * 1024;

    /**
     * {@link CollectorSampler#isSampled(Span) samples spans} to reduce load on the storage system.
     * Defaults to always sample.
     */
    public Builder sampler(CollectorSampler sampler) {
      this.sampler = checkNotNull(sampler, "sampler");
      return this;
    }

    /** Aggregates and reports collection metrics to a monitoring system. Defaults to no-op. */
    public Builder metrics(CollectorMetrics metrics) {
      this.metrics = checkNotNull(metrics, "metrics").forTransport("kafka");
      return this;
    }

    /** Topic zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder topic(String topic) {
      this.topic = checkNotNull(topic, "topic");
      return this;
    }

    /** The zookeeper connect string, ex. 127.0.0.1:2181. No default */
    public Builder zookeeper(String zookeeper) {
      this.zookeeper = checkNotNull(zookeeper, "zookeeper");
      return this;
    }

    /** The consumer group this process is consuming on behalf of. Defaults to "zipkin" */
    public Builder groupId(String groupId) {
      this.groupId = checkNotNull(groupId, "groupId");
      return this;
    }

    /** Count of threads/streams consuming the topic. Defaults to 1 */
    public Builder streams(int streams) {
      this.streams = streams;
      return this;
    }

    /** Maximum size of a message containing spans in bytes. Defaults to 1 MiB */
    public Builder maxMessageSize(int bytes) {
      this.maxMessageSize = bytes;
      return this;
    }

    /**
     * @param storage Once spans are sampled, they are {@link AsyncSpanConsumer#accept(List,
     * Callback) queued for storage} using this component.
     */
    public KafkaCollector build(StorageComponent storage) {
      checkNotNull(storage, "storage");
      return new KafkaCollector(this, new Lazy<AsyncSpanConsumer>() {
        @Override protected AsyncSpanConsumer compute() {
          AsyncSpanConsumer result = storage.asyncSpanConsumer(sampler, metrics);
          return checkNotNull(result, storage + ".asyncSpanConsumer()");
        }
      });
    }
  }

  final ZookeeperConsumerConnector connector;
  final ExecutorService pool;

  KafkaCollector(Builder builder, Lazy<AsyncSpanConsumer> consumer) {
    Map<String, Integer> topicCountMap = new LinkedHashMap<>(1);
    topicCountMap.put(builder.topic, builder.streams);

    // Settings below correspond to "Old Consumer Configs"
    // http://kafka.apache.org/documentation.html
    Properties props = new Properties();
    props.put("zookeeper.connect", builder.zookeeper);
    props.put("group.id", builder.groupId);
    props.put("fetch.message.max.bytes", String.valueOf(builder.maxMessageSize));
    // Same default as zipkin-scala, and keeps tests from hanging
    props.put("auto.offset.reset", "smallest");

    connector = (ZookeeperConsumerConnector) createJavaConsumerConnector(new ConsumerConfig(props));

    pool = builder.streams == 1
        ? Executors.newSingleThreadExecutor()
        : Executors.newFixedThreadPool(builder.streams);

    connector.createMessageStreams(topicCountMap)
        .get(builder.topic).forEach(stream ->
        pool.execute(new KafkaStreamProcessor(stream, consumer, builder.metrics))
    );
  }

  @Override
  public void close() {
    pool.shutdown();
    connector.shutdown();
  }
}
