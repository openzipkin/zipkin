/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.kafka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.collector.kafka.KafkaCollector;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinKafkaCollectorConfigurationTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesNotProvideCollectorComponent_whenBootstrapServersUnset() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinKafkaCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(KafkaCollector.class);
    });
  }

  @Test void providesCollectorComponent_whenBootstrapServersEmptyString() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      TestPropertyValues.of("zipkin.collector.kafka.bootstrap-servers:").applyTo(context);
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinKafkaCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(KafkaCollector.class);
    });
  }

  @Test void providesCollectorComponent_whenBootstrapServersSet() {
    TestPropertyValues.of("zipkin.collector.kafka.bootstrap-servers:localhost:9092")
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinKafkaCollectorConfiguration.class,
      InMemoryConfiguration.class);
    context.refresh();

    assertThat(context.getBean(KafkaCollector.class)).isNotNull();
  }

  @Test void doesNotProvidesCollectorComponent_whenBootstrapServersSetAndDisabled() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      TestPropertyValues.of("zipkin.collector.kafka.bootstrap-servers:localhost:9092")
        .applyTo(context);
      TestPropertyValues.of("zipkin.collector.kafka.enabled:false").applyTo(context);
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinKafkaCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(KafkaCollector.class);
    });
  }
}
