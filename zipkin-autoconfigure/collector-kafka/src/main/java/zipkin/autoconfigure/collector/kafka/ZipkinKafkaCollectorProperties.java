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
package zipkin.autoconfigure.collector.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin.collector.kafka.KafkaCollector;

@ConfigurationProperties("zipkin.collector.kafka")
public class ZipkinKafkaCollectorProperties {
  private String topic = "zipkin";
  private String zookeeper;
  private String groupId = "zipkin";
  private int streams = 1;
  private int maxMessageSize = 1024 * 1024;
  private Map<String, String> overrides = new LinkedHashMap<>();

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getZookeeper() {
    return zookeeper;
  }

  public void setZookeeper(String zookeeper) {
    this.zookeeper = "".equals(zookeeper) ? null : zookeeper;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public int getStreams() {
    return streams;
  }

  public void setStreams(int streams) {
    this.streams = streams;
  }

  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  public Map<String, String> getOverrides() {
    return overrides;
  }

  public void setOverrides(Map<String, String> overrides) {
    this.overrides = overrides;
  }

  public KafkaCollector.Builder toBuilder() {
    return KafkaCollector.builder()
        .topic(topic)
        .zookeeper(zookeeper)
        .groupId(groupId)
        .streams(streams)
        .maxMessageSize(maxMessageSize)
        .overrides(overrides);
  }
}
