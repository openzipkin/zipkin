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
package zipkin.server;

import com.github.kristofa.brave.Brave;
import io.prometheus.client.Histogram;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.actuate.health.OrderedHealthAggregator;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.buffer.CounterBuffers;
import org.springframework.boot.actuate.metrics.buffer.GaugeBuffers;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import zipkin.autoconfigure.prometheus.ZipkinPrometheusMetricsAutoConfiguration;
import zipkin.autoconfigure.ui.ZipkinUiAutoConfiguration;
import zipkin.internal.V2StorageComponent;
import zipkin.server.brave.BraveConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinServerConfigurationTest {
  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After public void close() {
    context.close();
  }

  @Test public void httpCollector_enabledByDefault() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      ZipkinHttpCollector.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinHttpCollector.class)).isNotNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void httpCollector_canDisable() {
    addEnvironment(context, "zipkin.collector.http.enabled:false");
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      ZipkinHttpCollector.class
    );
    context.refresh();

    context.getBean(ZipkinHttpCollector.class);
  }

  @Test public void query_enabledByDefault() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      ZipkinQueryApiV1.class,
      ZipkinQueryApiV2.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinQueryApiV1.class)).isNotNull();
    assertThat(context.getBean(ZipkinQueryApiV2.class)).isNotNull();
  }

  @Test public void query_canDisable() {
    addEnvironment(context, "zipkin.query.enabled:false");
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      ZipkinQueryApiV1.class,
      ZipkinQueryApiV2.class
    );
    context.refresh();

    try {
      context.getBean(ZipkinQueryApiV1.class);
      failBecauseExceptionWasNotThrown(NoSuchBeanDefinitionException.class);
    } catch (NoSuchBeanDefinitionException e) {
    }

    try {
      context.getBean(ZipkinQueryApiV2.class);
      failBecauseExceptionWasNotThrown(NoSuchBeanDefinitionException.class);
    } catch (NoSuchBeanDefinitionException e) {
    }
  }

  @Test public void providesHttpRequestDurationHistogram() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      ZipkinPrometheusMetricsAutoConfiguration.class
    );
    context.refresh();

    context.getBean(Histogram.class);
  }

  @Test public void providesHttpRequestDurationCustomizer() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      ZipkinPrometheusMetricsAutoConfiguration.class
    );
    context.refresh();

    context.getBean(UndertowDeploymentInfoCustomizer.class);
  }

  @Test public void ui_enabledByDefault() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      ZipkinUiAutoConfiguration.class
    );
    context.refresh();

    assertThat(context.getBean(ZipkinUiAutoConfiguration.class)).isNotNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void ui_canDisable() {
    addEnvironment(context, "zipkin.ui.enabled:false");
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      ZipkinUiAutoConfiguration.class
    );
    context.refresh();

    context.getBean(ZipkinUiAutoConfiguration.class);
  }

  @Test public void ActuateCollectorMetrics_buffersAreNotPresent() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class
    );
    context.refresh();

    try {
      context.getBean(CounterBuffers.class);
      failBecauseExceptionWasNotThrown(NoSuchBeanDefinitionException.class);
    } catch (NoSuchBeanDefinitionException e) {
    }

    try {
      context.getBean(GaugeBuffers.class);
      failBecauseExceptionWasNotThrown(NoSuchBeanDefinitionException.class);
    } catch (NoSuchBeanDefinitionException e) {
    }

    assertMetrics();
  }

  @Test public void selfTracing_canEnable() {
    addEnvironment(context, "zipkin.self-tracing.enabled:true");
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      BraveConfiguration.class
    );
    context.refresh();

    assertThat(context.getBean(Brave.class)).isNotNull();
  }

  @Test public void search_canDisable() {
    addEnvironment(context, "zipkin.storage.search-enabled:false");
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class
    );
    context.refresh();

    V2StorageComponent v2Storage = context.getBean(V2StorageComponent.class);
    assertThat(v2Storage.delegate())
      .extracting("searchEnabled")
      .containsExactly(false);
  }

  @Test public void ActuateCollectorMetrics_buffersArePresent() {
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.ConfigWithBuffers.class,
      ZipkinServerConfiguration.class
    );
    context.refresh();

    assertThat(context.getBean(CounterBuffers.class)).isNotNull();
    assertThat(context.getBean(GaugeBuffers.class)).isNotNull();

    assertMetrics();
  }

  private void assertMetrics() {
    ActuateCollectorMetrics metrics = context.getBean(ActuateCollectorMetrics.class);
    metrics.incrementBytes(20);
    assertThat(findMetric(metrics, "gauge.zipkin_collector.message_bytes").getValue())
      .isEqualTo(20.0d);
  }

  private Metric<?> findMetric(ActuateCollectorMetrics metrics, String metricName) {
    return metrics.metrics().stream().filter(m -> m.getName().equals(metricName)).findAny().get();
  }

  @Configuration
  public static class Config {
    @Bean
    public HealthAggregator healthAggregator() {
      return new OrderedHealthAggregator();
    }
  }

  @Configuration
  @Import(Config.class)
  public static class ConfigWithBuffers {
    @Bean
    public CounterBuffers counterBuffers() {
      return new CounterBuffers();
    }

    @Bean
    public GaugeBuffers gaugeBuffers() {
      return new GaugeBuffers();
    }
  }
}
