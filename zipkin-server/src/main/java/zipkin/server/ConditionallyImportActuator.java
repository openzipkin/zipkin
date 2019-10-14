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
package zipkin.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Actuator relies on classpath scanning, so we cannot choose one configuration entrypoint unless
 * using {@link EnableAutoConfiguration}. We disable scanning in {@link ZipkinServer} by default so
 * this conditionally loads actuator a different way.
 *
 * <p>This queries the property {@link #PROPERTY_NAME_ACTUATOR_INCLUDE} which cherry-picks the most
 * relevant configuration for when actuator is in the classpath.
 */
final class ConditionallyImportActuator
  implements ApplicationContextInitializer<GenericApplicationContext> {
  static final Logger LOG = LoggerFactory.getLogger(ConditionallyImportActuator.class);

  static final String ACTUATOR_IMPL_CLASS =
    "com.linecorp.armeria.spring.actuate.ArmeriaSpringActuatorAutoConfiguration";
  static final String PROPERTY_NAME_ACTUATOR_ENABLED = "zipkin.internal.actuator.enabled";
  static final String PROPERTY_NAME_ACTUATOR_INCLUDE = "zipkin.internal.actuator.include";

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
    String[] includes = Binder.get(env).bind(PROPERTY_NAME_ACTUATOR_INCLUDE, String[].class).get();
    if (includes == null) return;

    try {
      context.registerBean(Class.forName(ACTUATOR_IMPL_CLASS));
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
