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

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.activemq.ActiveMQCollector;

/** opens package access for testing */
public final class Access {

  /** Just registering properties to avoid automatically connecting to a ActiveMQ server */
  public static void registerActiveMQProperties(AnnotationConfigApplicationContext context) {
    context.register(
        PropertyPlaceholderAutoConfiguration.class, EnableActiveMQCollectorProperties.class);
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinActiveMQCollectorProperties.class)
  static class EnableActiveMQCollectorProperties {}

  public static ActiveMQCollector.Builder collectorBuilder(
      AnnotationConfigApplicationContext context) {
    return context.getBean(ZipkinActiveMQCollectorProperties.class).toBuilder();
  }
}
