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
package zipkin2.server.internal.rabbitmq;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.collector.rabbitmq.RabbitMQCollector;
import zipkin2.storage.StorageComponent;

/** Auto-configuration for {@link RabbitMQCollector}. */
@Configuration
@ConditionalOnProperty(name = "zipkin.collector.rabbitmq.enabled", havingValue = "true")
@EnableConfigurationProperties(ZipkinRabbitMQCollectorProperties.class)
public class ZipkinRabbitMQCollectorConfiguration {

  @Bean(initMethod = "start")
  RabbitMQCollector rabbitMq(
      ZipkinRabbitMQCollectorProperties properties,
      CollectorSampler sampler,
      CollectorMetrics metrics,
      StorageComponent storage)
      throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
    return properties.toBuilder().sampler(sampler).metrics(metrics).storage(storage).build();
  }
}
