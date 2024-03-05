/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.rabbitmq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.collector.rabbitmq.RabbitMQCollector;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinRabbitMQCollectorConfigurationTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesNotProvideCollectorComponent_whenAddressAndUriNotSet() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(RabbitMQCollector.class);
    });
  }

  @Test void doesNotProvideCollectorComponent_whenAddressesAndUriIsEmptyString() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context = new AnnotationConfigApplicationContext();
      TestPropertyValues.of(
        "zipkin.collector.rabbitmq.addresses:",
        "zipkin.collector.rabbitmq.uri:")
        .applyTo(context);
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(RabbitMQCollector.class);
    });
  }

  @Test void providesCollectorComponent_whenAddressesSet() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.collector.rabbitmq.addresses:localhost:1234").applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinRabbitMQCollectorConfiguration.class,
      InMemoryConfiguration.class);

    try {
      context.refresh();
      failBecauseExceptionWasNotThrown(BeanCreationException.class);
    } catch (BeanCreationException e) {
      assertThat(e.getCause()).hasMessageContaining(
        "Unable to establish connection to RabbitMQ server: Connection refused");
    }
  }

  @Test void doesNotProvidesCollectorComponent_whenAddressesSetAndDisabled() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context = new AnnotationConfigApplicationContext();
      TestPropertyValues.of("zipkin.collector.rabbitmq.addresses:localhost:1234").applyTo(context);
      TestPropertyValues.of("zipkin.collector.rabbitmq.enabled:false").applyTo(context);
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(RabbitMQCollector.class);
    });
  }
}
