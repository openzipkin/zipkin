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

import java.util.Properties;
import kafka.consumer.ConsumerConfig;

import static zipkin.internal.Util.checkNotNull;

/** Configuration including defaults needed to consume spans from a Kafka topic. */
public final class KafkaConfig {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String topic = "zipkin";
    private String zookeeper;
    private String groupId = "zipkin";
    private int streams = 1;

    /** Topic zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder topic(String topic) {
      this.topic = topic;
      return this;
    }

    /** The zookeeper connect string, ex. 127.0.0.1:2181. No default */
    public Builder zookeeper(String zookeeper) {
      this.zookeeper = zookeeper;
      return this;
    }

    /** The consumer group this process is consuming on behalf of. Defaults to "zipkin" */
    public Builder groupId(String groupId) {
      this.groupId = groupId;
      return this;
    }

    /** Count of threads/streams consuming the topic. Defaults to 1 */
    public Builder streams(int streams) {
      this.streams = streams;
      return this;
    }

    public KafkaConfig build() {
      return new KafkaConfig(this);
    }
  }

  final String topic;
  final String zookeeper;
  final String groupId;
  final int streams;

  KafkaConfig(Builder builder) {
    this.topic = checkNotNull(builder.topic, "topic");
    this.zookeeper = checkNotNull(builder.zookeeper, "zookeeper");
    this.groupId = checkNotNull(builder.groupId, "groupId");
    this.streams = builder.streams;
  }

  ConsumerConfig forConsumer() {
    Properties props = new Properties();
    props.put("zookeeper.connect", zookeeper);
    props.put("group.id", groupId);
    // Same default as zipkin-scala, and keeps tests from hanging
    props.put("auto.offset.reset", "smallest");
    return new ConsumerConfig(props);
  }
}
