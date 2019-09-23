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
package zipkin2.server.internal;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import zipkin2.CheckResult;
import zipkin2.Component;

final class ZipkinHealthIndicator implements HealthIndicator {
  final Component component;

  ZipkinHealthIndicator(Component component) {
    this.component = component;
  }

  /** synchronized to prevent overlapping requests to a storage backend */
  @Override public synchronized Health health() {
    CheckResult result = component.check();
    if (result.ok()) return Health.up().build();
    Throwable ex = result.error();
    // Like withException, but without the distracting ": null" when there is no message.
    String message = ex.getMessage();
    return Health.down()
      .withDetail("error", ex.getClass().getName() + (message != null ? ": " + message : ""))
      .build();
  }
}
