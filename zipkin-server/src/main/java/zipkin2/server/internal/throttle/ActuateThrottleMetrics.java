/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal.throttle;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

final class ActuateThrottleMetrics {
  private final MeterRegistry registryInstance;

  public ActuateThrottleMetrics(MeterRegistry registryInstance) {
    this.registryInstance = registryInstance;
  }

  public void bind(ExecutorService executor) {
    if(!(executor instanceof ThreadPoolExecutor)){
      return;
    }

    ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
    Gauge.builder("zipkin_storage.throttle.concurrency", pool::getCorePoolSize)
            .description("number of threads running storage requests")
            .register(registryInstance);
    Gauge.builder("zipkin_storage.throttle.queue_size", pool.getQueue()::size)
            .description("number of items queued waiting for access to storage")
            .register(registryInstance);
  }

  public void bind(Limiter<Void> limiter) {
    if(!(limiter instanceof AbstractLimiter)){
      return;
    }

    AbstractLimiter abstractLimiter = (AbstractLimiter) limiter;

    // This value should parallel (zipkin_storage.throttle.queue_size + zipkin_storage.throttle.concurrency)
    // It is tracked to make sure it doesn't perpetually increase.  If it does then we're not resolving LimitListeners.
    Gauge.builder("zipkin_storage.throttle.in_flight_requests", abstractLimiter::getInflight)
            .description("number of requests the limiter thinks are active")
            .register(registryInstance);
  }
}
