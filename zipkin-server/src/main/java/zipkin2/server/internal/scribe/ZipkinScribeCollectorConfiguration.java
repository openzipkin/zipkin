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
package zipkin2.server.internal.scribe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.collector.scribe.ScribeCollector;
import zipkin2.storage.StorageComponent;

/**
 * This collector accepts Scribe logs in a specified category. Each log entry is expected to contain
 * a single span, which is TBinaryProtocol big-endian, then base64 encoded. Decoded spans are stored
 * asynchronously.
 */
@Configuration
@ConditionalOnProperty(value = "zipkin.collector.scribe.enabled", havingValue = "true")
public class ZipkinScribeCollectorConfiguration {
  /** The init method will block until the scribe port is listening, or crash on port conflict */
  @Bean(initMethod = "start")
  ScribeCollector scribe(
    @Value("${zipkin.collector.scribe.category:zipkin}") String category,
    @Value("${zipkin.collector.scribe.port:9410}") int port,
    CollectorSampler sampler,
    CollectorMetrics metrics,
    StorageComponent storage) {
    return ScribeCollector.newBuilder()
      .category(category)
      .port(port)
      .sampler(sampler)
      .metrics(metrics)
      .storage(storage)
      .build();
  }
}
