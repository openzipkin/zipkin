/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.pulsar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.collector.pulsar.PulsarCollector;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinPulsarCollectorConfigurationTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesNotProvideCollectorComponent_whenBootstrapServersUnset() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context.register(
          PropertyPlaceholderAutoConfiguration.class,
          ZipkinPulsarCollectorConfiguration.class,
          InMemoryConfiguration.class);
      context.refresh();
      context.getBean(PulsarCollector.class);
    });
  }

  @Test void providesCollectorComponent_whenServiceUrlEmptyString() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      TestPropertyValues.of("zipkin.collector.pulsar.service-url:").applyTo(context);
      context.register(
          PropertyPlaceholderAutoConfiguration.class,
          ZipkinPulsarCollectorConfiguration.class,
          InMemoryConfiguration.class);
      context.refresh();
      context.getBean(PulsarCollector.class);
    });
  }

  @Test void providesCollectorComponent_whenServiceUrlServersSet() {
    TestPropertyValues.of("zipkin.collector.pulsar.service-url:pulsar://localhost:6650", 
            "zipkin.collector.pulsar.subscription-name:zipkin-subscriptionName")
        .applyTo(context);
    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinPulsarCollectorConfiguration.class,
        InMemoryConfiguration.class);
    try {
      context.refresh();
      failBecauseExceptionWasNotThrown(BeanCreationException.class);
    } catch (BeanCreationException e) {
      assertThat(e.getCause()).hasMessageContaining(
          "Pulsar unable to subscribe the topic");
    }
  }

  @Test void doesNotProvidesCollectorComponent_whenServiceUrlSetAndDisabled() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      TestPropertyValues.of("zipkin.collector.pulsar.service-url:pulsar://127.0.0.1:6650")
          .applyTo(context);
      TestPropertyValues.of("zipkin.collector.pulsar.enabled:false").applyTo(context);
      context.register(
          PropertyPlaceholderAutoConfiguration.class,
          ZipkinPulsarCollectorConfiguration.class,
          InMemoryConfiguration.class);
      context.refresh();
      context.getBean(PulsarCollector.class);
    });
  }
}
