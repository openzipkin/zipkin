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
package zipkin2.server.internal.throttle;

import com.netflix.concurrency.limits.Limiter.Listener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import zipkin2.collector.CollectorMetrics;

/** Follows the same naming convention as {@link CollectorMetrics} */
final class LimiterMetrics {
  final Counter requests, requestsSucceeded, requestsIgnored, requestsDropped;

  final Listener wrap(Listener delegate) {
    return new Listener() {
      @Override public void onSuccess() {
        // usually we don't add metrics like this,
        // but for now it is helpful to sanity check acquired vs erred.
        requestsSucceeded.increment();
        delegate.onSuccess();
      }

      @Override public void onIgnore() {
        requestsIgnored.increment();
        delegate.onIgnore();
      }

      @Override public void onDropped() {
        requestsDropped.increment();
        delegate.onDropped();
      }

      @Override public String toString() {
        return "LimiterMetrics{" + delegate + "}";
      }
    };
  }

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
