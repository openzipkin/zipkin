/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
