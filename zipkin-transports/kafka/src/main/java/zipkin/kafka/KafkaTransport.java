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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kafka.consumer.ConsumerConfig;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.serializer.StringDecoder;
import zipkin.AsyncSpanConsumer;
import zipkin.Sampler;
import zipkin.StorageComponent;
import zipkin.internal.Lazy;

import static kafka.consumer.Consumer.createJavaConsumerConnector;
import static zipkin.internal.Util.checkNotNull;

/**
 * This transport polls a Kafka topic for messages that contain TBinaryProtocol big-endian encoded
 * lists of spans. These spans are pushed to a {@link AsyncSpanConsumer#accept span consumer}.
 */
public final class KafkaTransport implements AutoCloseable {

  /** Configuration including defaults needed to consume spans from a Kafka topic. */
  public static final class Builder {
    String topic = "zipkin";
    String zookeeper;
    String groupId = "zipkin";
    int streams = 1;

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

    public KafkaTransport writeTo(StorageComponent storage, Sampler sampler) {
      checkNotNull(storage, "storage");
      checkNotNull(sampler, "sampler");
      return new KafkaTransport(this, new Lazy<AsyncSpanConsumer>() {
        @Override protected AsyncSpanConsumer compute() {
          return checkNotNull(storage.asyncSpanConsumer(sampler), storage + ".asyncSpanConsumer()");
        }
      });
    }
  }

  final ConsumerConnector connector;
  final ExecutorService pool;

  KafkaTransport(Builder builder, Lazy<AsyncSpanConsumer> consumer) {
    Map<String, Integer> topicCountMap = new LinkedHashMap<>(1);
    topicCountMap.put(builder.topic, builder.streams);

    Properties props = new Properties();
    props.put("zookeeper.connect", builder.zookeeper);
    props.put("group.id", builder.groupId);
    // Same default as zipkin-scala, and keeps tests from hanging
    props.put("auto.offset.reset", "smallest");

    connector = createJavaConsumerConnector(new ConsumerConfig(props));

    pool = builder.streams == 1
        ? Executors.newSingleThreadExecutor()
        : Executors.newFixedThreadPool(builder.streams);

    connector.createMessageStreams(topicCountMap, new StringDecoder(null), new SpansDecoder())
        .get(builder.topic).forEach(stream ->
        pool.execute(new KafkaStreamProcessor(stream, consumer))
    );
  }

  @Override
  public void close() {
    pool.shutdown();
    connector.shutdown();
  }
}
