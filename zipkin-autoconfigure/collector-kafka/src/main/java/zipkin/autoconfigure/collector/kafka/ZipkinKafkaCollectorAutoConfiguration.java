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
package zipkin.autoconfigure.collector.kafka;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.collector.kafka.KafkaCollector;
import zipkin.storage.StorageComponent;

/**
 * This collector consumes a topic, decodes spans from thrift messages and stores them subject to
 * sampling policy.
 */
@Configuration
@EnableConfigurationProperties(ZipkinKafkaCollectorProperties.class)
@Conditional(KafkaZooKeeperSetCondition.class)
public class ZipkinKafkaCollectorAutoConfiguration {

  /**
   * This launches a thread to run start. This prevents a several second hang, or worse crash if
   * zookeeper isn't running, yet.
   */
  @Bean KafkaCollector kafka(ZipkinKafkaCollectorProperties kafka, CollectorSampler sampler,
      CollectorMetrics metrics, StorageComponent storage) {
    final KafkaCollector result =
        kafka.toBuilder().sampler(sampler).metrics(metrics).storage(storage).build();

    // don't use @Bean(initMethod = "start") as it can crash the process if zookeeper is down
    Thread start = new Thread("start " + result.getClass().getSimpleName()) {
      @Override public void run() {
        result.start();
      }
    };
    start.setDaemon(true);
    start.start();

    return result;
  }
}
