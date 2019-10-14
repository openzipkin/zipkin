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

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Auto-configuration relies on classpath scanning, which is slow, and disabled in {@link
 * ZipkinServer} by default. This performs property-based lookup instead.
 *
 * <p>To use this, move autoconfiguration values from {@code src/main/resources/META-INF/spring.factories}
 * to map entries under the yaml path {@link #PROPERTY_NAME_MODULE}.
 *
 * <p>For example, add the following to {@code src/main/resources/zipkin-server-stackdriver.yml}:
 * <pre>{@code
 * zipkin:
 *   internal:
 *     module:
 *       stackdriver: zipkin.autoconfigure.storage.stackdriver.ZipkinStackdriverModule
 * }</pre>
 *
 * <h3>Rationale</h3>
 * Starting with Spring Boot 2.0, merging YAML lists from different profiles is no longer
 * supported.
 *
 * <p>For example, we couldn't do this as the last profile will overwrite the module list.
 * <pre>{@code
 * zipkin:
 *   internal:
 *     module:
 *       - zipkin.module.storage.stackdriver.ZipkinStackdriverModule
 * }</pre>
 *
 * <p>Since merging maps works, we can work around this by naming the configuration instead. Each
 * profile that defines the path {@link #PROPERTY_NAME_MODULE} will be merged. each profile, there
 * will be multiple keys under the path
 *
 * <p>Ex. zipkin-server-sqs.yml
 * <pre>{@code
 * zipkin:
 *   internal:
 *     module:
 *       sqs: zipkin.module.collector.sqs.ZipkinSQSCollectorModule
 * }</pre>
 *
 * <p>and zipkin-server-kinesis.yml
 * <pre>{@code
 * zipkin:
 *   internal:
 *     module:
 *       kinesis: zipkin.module.collector.kinesis.ZipkinKinesisCollectorModule
 * }</pre>
 *
 * <p>merges as if it were the below:
 * <pre>{@code
 * zipkin:
 *   internal:
 *     module:
 *       sqs: zipkin.module.collector.sqs.ZipkinSQSCollectorModule
 *       kinesis: zipkin.module.collector.kinesis.ZipkinKinesisCollectorModule
 * }</pre>
 */
final class ModuleImporter implements ApplicationContextInitializer<GenericApplicationContext> {
  static final Logger LOG = LoggerFactory.getLogger(ModuleImporter.class);

  static final String PROPERTY_NAME_MODULE = "zipkin.internal.module";

  @Override public void initialize(GenericApplicationContext context) {
    ConfigurableEnvironment env = context.getEnvironment();

    // At this point in the life-cycle, env can directly resolve plain properties, like the boolean
    // above. If you tried to resolve a property bound by a yaml map, it returns null, as they are
    // not yet bound.
    //
    // As we are in a configurable environment, we can bind lists properties. We expect this to take
    // includes from PROPERTY_NAME_MODULE yaml path from all modules.
    Map<String, String> modules =
      Binder.get(env).bind(PROPERTY_NAME_MODULE, Map.class).orElse(null);
    if (modules == null || modules.isEmpty()) {
      LOG.debug("no modules found under path " + PROPERTY_NAME_MODULE);
      return;
    }

    LOG.debug("attempting to load modules: " + modules.keySet());
    for (Map.Entry<String, String> module : modules.entrySet()) {
      try {
        context.registerBean(Class.forName(module.getValue()));
      } catch (Exception e) {
        // Skip any classes that didn't match due to drift
        LOG.debug("skipping unloadable module " + module.getKey(), e);
      }
    }
  }
}
