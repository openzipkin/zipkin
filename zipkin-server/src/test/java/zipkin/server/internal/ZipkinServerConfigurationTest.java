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
package zipkin.server.internal;

import brave.Tracing;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.actuate.health.OrderedHealthAggregator;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.internal.V2StorageComponent;
import zipkin.server.internal.brave.TracingConfiguration;

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

  @Test public void selfTracing_canEnable() {
    addEnvironment(context, "zipkin.self-tracing.enabled:true");
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinServerConfigurationTest.Config.class,
      ZipkinServerConfiguration.class,
      TracingConfiguration.class
    );
    context.refresh();

    context.getBean(Tracing.class).close();
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

  @Configuration
  public static class Config {
    @Bean
    public HealthAggregator healthAggregator() {
      return new OrderedHealthAggregator();
    }

    @Bean
    MeterRegistry registry () {
      return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
  }
}
