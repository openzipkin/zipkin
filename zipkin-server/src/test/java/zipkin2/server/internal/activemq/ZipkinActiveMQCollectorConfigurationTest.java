/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.activemq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.collector.activemq.ActiveMQCollector;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinActiveMQCollectorConfigurationTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesNotProvideCollectorComponent_whenAddressAndUriNotSet() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinActiveMQCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(ActiveMQCollector.class);
    });
  }

  @Test void providesCollectorComponent_whenUrlSet() {
    TestPropertyValues.of("zipkin.collector.activemq.url=vm://localhost")
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinActiveMQCollectorConfiguration.class,
      InMemoryConfiguration.class);

    try {
      context.refresh();
      failBecauseExceptionWasNotThrown(BeanCreationException.class);
    } catch (BeanCreationException e) {
      assertThat(e.getCause()).hasMessage(
        "Unable to establish connection to ActiveMQ broker: Transport scheme NOT recognized: [vm]");
    }
  }
}
