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

package zipkin.server.receiver.zipkin.kafka;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;

public class ZipkinKafkaReceiverConfig extends ModuleConfig {

  /**
   *  A list of host/port pairs to use for establishing the initial connection to the Kafka cluster.
   */
  private String kafkaBootstrapServers;
  private String kafkaGroupId = "zipkin";
  private String kafkaTopic = "zipkin";
  /**
   * Kafka consumer config,JSON format as Properties. If it contains the same key with above, would override.
   */
  private String kafkaConsumerConfig = "{\"auto.offset.reset\":\"earliest\",\"enable.auto.commit\":true}";
  private int kafkaConsumers = 1;
  private int kafkaHandlerThreadPoolSize;
  private int kafkaHandlerThreadPoolQueueSize;

  public ZipkinReceiverConfig toSkyWalkingConfig() {
    final ZipkinReceiverConfig config = new ZipkinReceiverConfig();
    config.setEnableKafkaCollector(true);
    config.setKafkaBootstrapServers(kafkaBootstrapServers);
    config.setKafkaGroupId(kafkaGroupId);
    config.setKafkaTopic(kafkaTopic);
    config.setKafkaConsumerConfig(kafkaConsumerConfig);
    config.setKafkaConsumers(kafkaConsumers);
    config.setKafkaHandlerThreadPoolSize(kafkaHandlerThreadPoolSize);
    config.setKafkaHandlerThreadPoolQueueSize(kafkaHandlerThreadPoolQueueSize);
    return config;
  }

  public String getKafkaBootstrapServers() {
    return kafkaBootstrapServers;
  }

  public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
    this.kafkaBootstrapServers = kafkaBootstrapServers;
  }

  public String getKafkaGroupId() {
    return kafkaGroupId;
  }

  public void setKafkaGroupId(String kafkaGroupId) {
    this.kafkaGroupId = kafkaGroupId;
  }

  public String getKafkaTopic() {
    return kafkaTopic;
  }

  public void setKafkaTopic(String kafkaTopic) {
    this.kafkaTopic = kafkaTopic;
  }

  public String getKafkaConsumerConfig() {
    return kafkaConsumerConfig;
  }

  public void setKafkaConsumerConfig(String kafkaConsumerConfig) {
    this.kafkaConsumerConfig = kafkaConsumerConfig;
  }

  public int getKafkaConsumers() {
    return kafkaConsumers;
  }

  public void setKafkaConsumers(int kafkaConsumers) {
    this.kafkaConsumers = kafkaConsumers;
  }

  public int getKafkaHandlerThreadPoolSize() {
    return kafkaHandlerThreadPoolSize;
  }

  public void setKafkaHandlerThreadPoolSize(int kafkaHandlerThreadPoolSize) {
    this.kafkaHandlerThreadPoolSize = kafkaHandlerThreadPoolSize;
  }

  public int getKafkaHandlerThreadPoolQueueSize() {
    return kafkaHandlerThreadPoolQueueSize;
  }

  public void setKafkaHandlerThreadPoolQueueSize(int kafkaHandlerThreadPoolQueueSize) {
    this.kafkaHandlerThreadPoolQueueSize = kafkaHandlerThreadPoolQueueSize;
  }
}
