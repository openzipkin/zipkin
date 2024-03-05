/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.throttle;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import zipkin2.collector.CollectorMetrics;

/** Follows the same naming convention as {@link CollectorMetrics} */
final class LimiterMetrics {
  final Counter requests, requestsSucceeded, requestsIgnored, requestsDropped;

  LimiterMetrics(MeterRegistry registry) {
    requests = Counter.builder("zipkin_storage.throttle.requests")
      .description("cumulative amount of limiter requests acquired")
      .register(registry);
    requestsSucceeded = Counter.builder("zipkin_storage.throttle.requests_succeeded")
      .description("cumulative amount of limiter requests acquired that later succeeded")
      .register(registry);
    requestsDropped =
      Counter.builder("zipkin_storage.throttle.requests_dropped")
        .description(
          "cumulative amount of limiter requests acquired that later dropped due to capacity")
        .register(registry);
    requestsIgnored =
      Counter.builder("zipkin_storage.throttle.requests_ignored")
        .description(
          "cumulative amount of limiter requests acquired that later dropped not due to capacity")
        .register(registry);
  }
}
