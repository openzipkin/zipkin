/**
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
package zipkin.server.internal;

import org.springframework.boot.actuate.health.CompositeHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.actuate.health.HealthIndicator;
import zipkin.Component;
import zipkin.internal.V2StorageComponent;

final class ZipkinHealthIndicator extends CompositeHealthIndicator {

  ZipkinHealthIndicator(HealthAggregator healthAggregator) {
    super(healthAggregator);
  }

  void addComponent(Component component) {
    String healthName = component instanceof V2StorageComponent
      ? ((V2StorageComponent) component).delegate().getClass().getSimpleName()
      : component.getClass().getSimpleName();
    healthName = healthName.replace("AutoValue_", "");
    addHealthIndicator(healthName, new ComponentHealthIndicator(component));
  }

  static final class ComponentHealthIndicator implements HealthIndicator {
    final Component component;

    ComponentHealthIndicator(Component component) {
      this.component = component;
    }

    /** synchronized to prevent overlapping requests to a storage backend */
    @Override public synchronized Health health() {
      Component.CheckResult result = component.check();
      return result.ok ? Health.up().build() : Health.down(result.exception).build();
    }
  }
}
