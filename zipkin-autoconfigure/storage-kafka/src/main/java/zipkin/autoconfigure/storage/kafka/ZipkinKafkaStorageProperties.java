/**
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
package zipkin.autoconfigure.storage.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.storage.kafka.KafkaStorage;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("zipkin.storage.kafka")
class ZipkinKafkaStorageProperties implements Serializable {
  /** Comma-separated list of Kafka bootstrap servers in the form [host]:[port],... */
  private String bootstrapServers;
  /** Kafka consumer group id used by the collector. */
  private String topic;
  /** Additional Kafka consumer configuration. */
  private Map<String, String> overrides = new LinkedHashMap<>();
  private KafkaProducer<byte[], byte[]> kafkaProducer;

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = emptyToNull(bootstrapServers);
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = emptyToNull(topic);
  }

  public Map<String, String> getOverrides() {
    return overrides;
  }

  public void setOverrides(Map<String, String> overrides) {
    this.overrides = overrides;
  }

  public KafkaStorage.Builder toBuilder() {
    final KafkaStorage.Builder result = KafkaStorage.newBuilder();
    if (bootstrapServers != null) result.bootstrapServers(bootstrapServers);
    if (topic != null) result.topic(topic);
    if (overrides != null) result.overrides(overrides);
    result.createProducer();
    return result;
  }

  private static String emptyToNull(String s) {
    return "".equals(s) ? null : s;
  }
}
