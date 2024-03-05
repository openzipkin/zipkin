/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import brave.Tracing;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import zipkin2.server.internal.brave.ZipkinSelfTracingConfiguration;
import zipkin2.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinHttpConfigurationTest {
  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void httpCollector_enabledByDefault() {
    registerBaseConfig(context);
    context.register(ZipkinHttpCollector.class);
    context.refresh();

    assertThat(context.getBean(ZipkinHttpCollector.class)).isNotNull();
  }

  @Test void httpCollector_canDisable() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      TestPropertyValues.of("zipkin.collector.http.enabled:false").applyTo(context);
      registerBaseConfig(context);
      context.register(ZipkinHttpCollector.class);
      context.refresh();

      context.getBean(ZipkinHttpCollector.class);
    });
  }

  @Test void query_enabledByDefault() {
    registerBaseConfig(context);
    context.register(ZipkinQueryApiV2.class);
    context.refresh();

    assertThat(context.getBean(ZipkinQueryApiV2.class)).isNotNull();
  }

  @Test void query_canDisable() {
    TestPropertyValues.of("zipkin.query.enabled:false").applyTo(context);
    registerBaseConfig(context);
    context.register(ZipkinQueryApiV2.class);
    context.refresh();

    assertThatThrownBy(() -> context.getBean(ZipkinQueryApiV2.class))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @Test void selfTracing_canEnable() {
    TestPropertyValues.of("zipkin.self-tracing.enabled:true").applyTo(context);
    registerBaseConfig(context);
    context.register(ZipkinSelfTracingConfiguration.class);
    context.refresh();

    context.getBean(Tracing.class).close();
  }

  @Test void search_canDisable() {
    TestPropertyValues.of("zipkin.storage.search-enabled:false").applyTo(context);
    registerBaseConfig(context);
    context.refresh();

    StorageComponent v2Storage = context.getBean(StorageComponent.class);
    assertThat(v2Storage)
      .extracting("searchEnabled")
      .isEqualTo(false);
  }

  @Configuration
  public static class Config {
    @Bean MeterRegistry registry() {
      return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Bean ConversionService conversionService() {
      return ApplicationConversionService.getSharedInstance();
    }
  }

  static void registerBaseConfig(AnnotationConfigApplicationContext context) {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      Config.class,
      ZipkinConfiguration.class,
      ZipkinHttpConfiguration.class
    );
  }
}
