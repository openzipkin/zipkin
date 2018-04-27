/**
 * Copyright 2015-2018 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.autoconfigure.prometheus;

import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static io.micrometer.core.instrument.Meter.Type.COUNTER;
import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinPrometheusMetricsAutoConfigurationTest {
  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @Before public void refresh() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      MetricsAutoConfiguration.class,
      PrometheusMetricsExportAutoConfiguration.class,
      ZipkinPrometheusMetricsAutoConfiguration.class
    );
    context.refresh();
  }

  @After public void close() {
    context.close();
  }

  @Test public void providesHttpRequestDurationCustomizer() {
    context.getBean(UndertowDeploymentInfoCustomizer.class);
  }

  /** old naming convention didn't end in _total */
  @Test public void usesOldNamingConvention() {
    NamingConvention prometheusNamingConvention =
      context.getBean(PrometheusMeterRegistry.class).config().namingConvention();
    assertThat(prometheusNamingConvention.name("counter.zipkin_collector.messages.http", COUNTER))
      .doesNotEndWith("_total");
  }
}
