/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.prometheus;

import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinPrometheusMetricsConfigurationTest {
  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  public void refresh() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinPrometheusMetricsConfiguration.class
    );
    context.refresh();
  }

  @AfterEach void close() {
    context.close();
  }

  @Test void providesHttpRequestDurationCustomizer() {
    refresh();

    context.getBeansOfType(ArmeriaServerConfigurator.class);
  }

  @Test void defaultMetricName() {
    refresh();

    assertThat(context.getBean(ZipkinPrometheusMetricsConfiguration.class).metricName)
      .isEqualTo("http.server.requests");
  }

  @Test void overrideMetricName() {
    TestPropertyValues.of("management.metrics.web.server.requests-metric-name:foo")
      .applyTo(context);
    refresh();

    assertThat(context.getBean(ZipkinPrometheusMetricsConfiguration.class).metricName)
      .isEqualTo("foo");
  }
}
