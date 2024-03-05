/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.kafka;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.kafka.KafkaCollector;

/** opens package access for testing */
public final class Access {

  /** Just registering properties to avoid automatically connecting to a Kafka server */
  public static void registerKafkaProperties(AnnotationConfigApplicationContext context) {
    context.register(
        PropertyPlaceholderAutoConfiguration.class, EnableKafkaCollectorProperties.class);
  }

  @Configuration
  @EnableConfigurationProperties(ZipkinKafkaCollectorProperties.class)
  static class EnableKafkaCollectorProperties {}

  public static KafkaCollector.Builder collectorBuilder(
      AnnotationConfigApplicationContext context) {
    return context.getBean(ZipkinKafkaCollectorProperties.class).toBuilder();
  }
}
