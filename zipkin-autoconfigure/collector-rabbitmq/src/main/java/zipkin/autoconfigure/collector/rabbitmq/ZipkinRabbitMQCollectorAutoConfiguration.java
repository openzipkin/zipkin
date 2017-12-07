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
package zipkin.autoconfigure.collector.rabbitmq;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.collector.rabbitmq.RabbitMQCollector;
import zipkin.storage.StorageComponent;

/**
 * Auto-configuration for {@link RabbitMQCollector}.
 */
@Configuration
@Conditional(ZipkinRabbitMQCollectorAutoConfiguration.RabbitMqAddressesSet.class)
@EnableConfigurationProperties(ZipkinRabbitMQCollectorProperties.class)
public class ZipkinRabbitMQCollectorAutoConfiguration {

  @Bean(initMethod = "start") RabbitMQCollector rabbitMq(
    ZipkinRabbitMQCollectorProperties properties,
    CollectorSampler sampler, CollectorMetrics metrics, StorageComponent storage)
    throws NoSuchAlgorithmException, KeyManagementException {
    return properties.toBuilder().sampler(sampler).metrics(metrics).storage(storage).build();
  }

  /**
   * This condition passes when {@link ZipkinRabbitMQCollectorProperties#getAddresses()} is set to
   * non-empty.
   *
   * <p>This is here because the yaml defaults this property to empty like this, and Spring Boot
   * doesn't have an option to treat empty properties as unset.
   *
   * <pre>{@code
   * addresses: ${RABBIT_ADDRESSES:}
   * }</pre>
   */
  static final class RabbitMqAddressesSet implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata a) {
      return !isEmpty(context.getEnvironment().getProperty("zipkin.collector.rabbitmq.addresses"));
    }

    private static boolean isEmpty(String s) {
      return s == null || s.isEmpty();
    }
  }
}
