/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

@Configuration
public class InMemoryConfiguration {
  @Bean public CollectorSampler sampler() {
    return CollectorSampler.ALWAYS_SAMPLE;
  }

  @Bean public CollectorMetrics metrics() {
    return CollectorMetrics.NOOP_METRICS;
  }

  @Bean public StorageComponent storage() {
    return InMemoryStorage.newBuilder().build();
  }
}
