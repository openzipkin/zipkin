/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.scribe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.collector.scribe.ScribeCollector;
import zipkin2.storage.StorageComponent;

/**
 * This collector accepts Scribe logs in a specified category. Each log entry is expected to contain
 * a single span, which is TBinaryProtocol big-endian, then base64 encoded. Decoded spans are stored
 * asynchronously.
 */
@ConditionalOnClass(ScribeCollector.class)
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
