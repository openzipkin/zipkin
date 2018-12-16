/*
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
package zipkin2.autoconfigure.server.grpc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.server.grpc.GrpcCollector;
import zipkin2.storage.StorageComponent;

/**
 * This creates a server for collecting spans via GRPC.
 */
@Configuration
@EnableConfigurationProperties(ZipkinGrpcServerProperties.class)
@ConditionalOnProperty(name = "zipkin.collector.grpc.enabled", matchIfMissing = true)
class ZipkinGrpcServerAutoConfiguration { // makes simple type name unique for /actuator/conditions

  @Bean(initMethod = "start", destroyMethod = "close")
  GrpcCollector grpcCollector(
      ZipkinGrpcServerProperties properties,
      CollectorSampler sampler,
      CollectorMetrics metrics,
      StorageComponent storage) {
    return properties.toBuilder().sampler(sampler).metrics(metrics).storage(storage).build();
  }

}
