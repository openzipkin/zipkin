/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.collector.kafka.KafkaCollector;

@ConfigurationProperties("zipkin.collector.kafka")
class ZipkinKafkaCollectorProperties {
  /** Comma-separated list of Kafka bootstrap servers in the form [host]:[port],... */
  private String bootstrapServers;
  /** Kafka consumer group id used by the collector. */
  private String groupId;
  /** Kafka topic span data will be retrieved from. */
  private String topic;
  /** Number of Kafka consumer threads to run. */
  private Integer streams;
  /** Additional Kafka consumer configuration. */
  private Map<String, String> overrides = new LinkedHashMap<>();

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = emptyToNull(bootstrapServers);
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = emptyToNull(groupId);
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = emptyToNull(topic);
  }

  public Integer getStreams() {
    return streams;
  }

  public void setStreams(Integer streams) {
    this.streams = streams;
  }

  public Map<String, String> getOverrides() {
    return overrides;
  }

  public void setOverrides(Map<String, String> overrides) {
    this.overrides = overrides;
  }

  public KafkaCollector.Builder toBuilder() {
    final KafkaCollector.Builder result = KafkaCollector.builder();
    if (bootstrapServers != null) result.bootstrapServers(bootstrapServers);
    if (groupId != null) result.groupId(groupId);
    if (topic != null) result.topic(topic);
    if (streams != null) result.streams(streams);
    if (overrides != null) result.overrides(overrides);
    return result;
  }

  private static String emptyToNull(String s) {
    return "".equals(s) ? null : s;
  }
}
