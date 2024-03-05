/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.activemq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.collector.activemq.ActiveMQCollector;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipkinActiveMQCollectorPropertiesTest {
  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  /** This prevents an empty ACTIVEMQ_URL variable from being mistaken as a real one */
  @Test void ignoresEmptyURL() {
    ZipkinActiveMQCollectorProperties properties = new ZipkinActiveMQCollectorProperties();
    properties.setUrl("");

    assertThat(properties.getUrl()).isNull();
  }

  @Test void providesCollectorComponent_whenUrlSet() {
    TestPropertyValues.of("zipkin.collector.activemq.url:tcp://localhost:61611") // wrong port
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinActiveMQCollectorConfiguration.class,
      InMemoryConfiguration.class);

    assertThatThrownBy(context::refresh)
      .isInstanceOf(BeanCreationException.class)
      .hasMessageContaining("Unable to establish connection to ActiveMQ broker");
  }

  @Test void doesNotProvidesCollectorComponent_whenUrlSetAndDisabled() {
    TestPropertyValues.of("zipkin.collector.activemq.url:tcp://localhost:61616")
      .applyTo(context);
    TestPropertyValues.of("zipkin.collector.activemq.enabled:false").applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinActiveMQCollectorConfiguration.class,
      InMemoryConfiguration.class);
    context.refresh();

    assertThatThrownBy(() -> context.getBean(ActiveMQCollector.class))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }
}
