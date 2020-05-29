/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * When auto-configuration is enabled, actuator and all of its subordinate endpoints such as {@code
 * BeansEndpointAutoConfiguration} load, subject to further conditions like {@code
 * management.endpoint.health.enabled=false}. When auto-configuration is disabled, these
 * configuration discovered indirectly via {@code META-INF/spring.factories} are no longer loaded.
 * This type helps load the actuator functionality we currently support without a compilation
 * dependency on actuator, and without relying on auto-configuration being enabled.
 *
 * <p><h3>Implementation note</h3>
 * <p>It may be possible to re-implement this as {@link ImportSelector} to provide {@link
 * #ACTUATOR_IMPL_CLASS} and the endpoint configuration types from {@link
 * #PROPERTY_NAME_ACTUATOR_INCLUDE}.
 */
// look at RATIONALE.md and update if relevant when changing this file
public final class ZipkinActuatorImporter
  implements ApplicationContextInitializer<GenericApplicationContext> {
  static final Logger LOG = LoggerFactory.getLogger(ZipkinActuatorImporter.class);

  static final String ACTUATOR_IMPL_CLASS =
    "com.linecorp.armeria.spring.actuate.ArmeriaSpringActuatorAutoConfiguration";
  static final String PROPERTY_NAME_ACTUATOR_ENABLED = "zipkin.internal.actuator.enabled";
  static final String PROPERTY_NAME_ACTUATOR_INCLUDE = "zipkin.internal.actuator.include";

  final String actuatorImplClass;

  public ZipkinActuatorImporter() {
    this(ACTUATOR_IMPL_CLASS);
  }

  ZipkinActuatorImporter(String actuatorImplClass) { // visible for testing
    this.actuatorImplClass = actuatorImplClass;
  }

  @Override public void initialize(GenericApplicationContext context) {
    ConfigurableEnvironment env = context.getEnvironment();
    if ("false".equalsIgnoreCase(env.getProperty(PROPERTY_NAME_ACTUATOR_ENABLED))) {
      LOG.debug("skipping actuator as it is disabled");
      return;
    }

    // At this point in the life-cycle, env can directly resolve plain properties, like the boolean
    // above. If you tried to resolve a property bound by a yaml list, it returns null, as they are
    // not yet bound.
    //
    // As we are in a configurable environment, we can bind lists properties. We expect this to take
    // includes from PROPERTY_NAME_ACTUATOR_INCLUDE yaml path of zipkin-server-shared.yml.
    String[] includes =
      Binder.get(env).bind(PROPERTY_NAME_ACTUATOR_INCLUDE, String[].class).orElse(null);
    if (includes == null || includes.length == 0) {
      LOG.debug("no actuator configuration found under path " + PROPERTY_NAME_ACTUATOR_INCLUDE);
      return;
    }

    LOG.debug("attempting to load actuator configuration: " + Arrays.toString(includes));
    try {
      context.registerBean(Class.forName(actuatorImplClass));
    } catch (Exception e) {
      LOG.debug("skipping actuator as implementation is not available", e);
      return;
    }

    for (String include : includes) {
      try {
        context.registerBean(Class.forName(include));
      } catch (Exception e) {
        // Skip any classes that didn't match due to drift
        LOG.debug("skipping unloadable actuator config " + include, e);
      }
    }
  }
}
