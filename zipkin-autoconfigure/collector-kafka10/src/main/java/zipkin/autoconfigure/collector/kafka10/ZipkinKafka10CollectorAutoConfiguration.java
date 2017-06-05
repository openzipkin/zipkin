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
package zipkin.autoconfigure.collector.kafka10;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.collector.kafka10.KafkaCollector;
import zipkin.storage.StorageComponent;

/**
 * This collector consumes a topic, decodes spans from thrift messages and stores them subject to
 * sampling policy.
 */
@Configuration
@EnableConfigurationProperties(ZipkinKafkaCollectorProperties.class)
@Conditional(ZipkinKafka10CollectorAutoConfiguration.KafkaBootstrapServersSet.class)
public class ZipkinKafka10CollectorAutoConfiguration { // makes simple type name unique for /autoconfig

  @Bean(initMethod = "start") KafkaCollector kafka(ZipkinKafkaCollectorProperties properties,
      CollectorSampler sampler, CollectorMetrics metrics, StorageComponent storage) {
    return  properties.toBuilder().sampler(sampler).metrics(metrics).storage(storage).build();
  }

  /**
   * This condition passes when {@link ZipkinKafkaCollectorProperties#getBootstrapServers()} is set to
   * non-empty.
   *
   * <p>This is here because the yaml defaults this property to empty like this, and spring-boot
   * doesn't have an option to treat empty properties as unset.
   *
   * <pre>{@code
   * bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:}
   * }</pre>
   */
  static final class KafkaBootstrapServersSet implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata a) {
      return !isEmpty(context.getEnvironment()
          .getProperty("zipkin.collector.kafka.bootstrap-servers"));
    }

    private static boolean isEmpty(String s) {
      return s == null || s.isEmpty();
    }
  }
}
