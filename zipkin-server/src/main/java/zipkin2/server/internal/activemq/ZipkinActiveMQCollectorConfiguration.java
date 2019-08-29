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
package zipkin2.server.internal.activemq;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.collector.activemq.ActiveMQCollector;
import zipkin2.storage.StorageComponent;

/** Auto-configuration for {@link ActiveMQCollector}. */
@Configuration
@EnableConfigurationProperties(ZipkinActiveMQCollectorProperties.class)
@Conditional(ZipkinActiveMQCollectorConfiguration.ActiveMQUrlSet.class)
public class ZipkinActiveMQCollectorConfiguration {

  @Bean(initMethod = "start")
  ActiveMQCollector activeMq(
    ZipkinActiveMQCollectorProperties properties,
    CollectorSampler sampler,
    CollectorMetrics metrics,
    StorageComponent storage) {
    return properties.toBuilder().sampler(sampler).metrics(metrics).storage(storage).build();
  }

  /**
   * This condition passes when {@link ZipkinActiveMQCollectorProperties#getUrl()}} is set to
   * non-empty.
   *
   * <p>This is here because the yaml defaults this property to empty like this, and spring-boot
   * doesn't have an option to treat empty properties as unset.
   *
   * <pre>{@code
   * url: ${ACTIVEMQ_URL:}
   * }</pre>
   */
  static final class ActiveMQUrlSet implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata a) {
      return !isEmpty(
        context.getEnvironment().getProperty("zipkin.collector.activemq.url")) &&
        notFalse(context.getEnvironment().getProperty("zipkin.collector.activemq.enabled"));
    }

    private static boolean isEmpty(String s) {
      return s == null || s.isEmpty();
    }

    private static boolean notFalse(String s){
      return s == null || !s.equals("false");
    }
  }
}
