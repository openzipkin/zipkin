/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.scribe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.server.internal.InMemoryConfiguration;
import zipkin2.server.internal.scribe.ZipkinScribeCollectorConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

public class ZipkinScribeCollectorConfigurationTest {
  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesntProvidesCollectorComponent_byDefault() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      refreshContext();

      context.getBean(ScribeCollector.class);
    });
  }

  /**
   * Note: this will flake if you happen to be running a server on port 9410!
   */
  @Test void providesCollectorComponent_whenEnabled() {
    TestPropertyValues.of("zipkin.collector.scribe.enabled:true").applyTo(context);
    refreshContext();

    assertThat(context.getBean(ScribeCollector.class)).isNotNull();
  }

  @Test void canOverrideProperty_port() {
    TestPropertyValues.of(
      "zipkin.collector.scribe.enabled:true",
      "zipkin.collector.scribe.port:9999")
      .applyTo(context);
    refreshContext();

    assertThat(context.getBean(ScribeCollector.class).server.port)
      .isEqualTo(9999);
  }

  public void refreshContext() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinScribeCollectorConfiguration.class,
      InMemoryConfiguration.class);
    context.refresh();
  }
}
