/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.throttle;

import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ThreadPoolExecutor;
import zipkin2.server.internal.MicrometerCollectorMetrics;

/** Follows the same naming convention as {@link MicrometerCollectorMetrics} */
final class MicrometerThrottleMetrics {
  final MeterRegistry registryInstance;

  MicrometerThrottleMetrics(MeterRegistry registryInstance) {
    this.registryInstance = registryInstance;
  }

  void bind(ThreadPoolExecutor pool) {
    Gauge.builder("zipkin_storage.throttle.concurrency", pool::getCorePoolSize)
      .description("number of threads running storage requests")
      .register(registryInstance);
    Gauge.builder("zipkin_storage.throttle.queue_size", pool.getQueue()::size)
      .description("number of items queued waiting for access to storage")
      .register(registryInstance);
  }

  void bind(AbstractLimiter limiter) {
    // This value should parallel (zipkin_storage.throttle.queue_size + zipkin_storage.throttle.concurrency)
    // It is tracked to make sure it doesn't perpetually increase.  If it does then we're not resolving LimitListeners.
    Gauge.builder("zipkin_storage.throttle.in_flight_requests", limiter::getInflight)
      .description("number of requests the limiter thinks are active")
      .register(registryInstance);
  }
}
