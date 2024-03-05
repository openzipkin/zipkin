/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.rabbitmq;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.rabbitmq.RabbitMQCollector;

/** opens package access for testing */
public final class Access {

  /** Just registering properties to avoid automatically connecting to a Rabbit MQ server */
  public static void registerRabbitMQProperties(AnnotationConfigApplicationContext context) {
    context.register(
        PropertyPlaceholderAutoConfiguration.class, EnableRabbitMQCollectorProperties.class);
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinRabbitMQCollectorProperties.class)
  static class EnableRabbitMQCollectorProperties {}

  public static RabbitMQCollector.Builder collectorBuilder(
      AnnotationConfigApplicationContext context) throws Exception {
    return context.getBean(ZipkinRabbitMQCollectorProperties.class).toBuilder();
  }
}
