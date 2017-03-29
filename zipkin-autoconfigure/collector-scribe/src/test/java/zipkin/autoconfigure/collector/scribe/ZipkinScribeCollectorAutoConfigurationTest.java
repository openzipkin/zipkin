/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.autoconfigure.collector.scribe;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.collector.scribe.ScribeCollector;
import zipkin.storage.InMemoryStorage;
import zipkin.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinScribeCollectorAutoConfigurationTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  AnnotationConfigApplicationContext context;

  @After
  public void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void doesntProvidesCollectorComponent_byDefault() {
    context = new AnnotationConfigApplicationContext();
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinScribeCollectorAutoConfiguration.class, InMemoryConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(ScribeCollector.class);
  }

  /** Note: this will flake if you happen to be running a server on port 9410! */
  @Test
  public void providesCollectorComponent_whenEnabled() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.collector.scribe.enabled:true");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinScribeCollectorAutoConfiguration.class, InMemoryConfiguration.class);
    context.refresh();

    assertThat(context.getBean(ScribeCollector.class)).isNotNull();
  }

  @Test
  public void canOverrideProperty_port() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context,
        "zipkin.collector.scribe.enabled:true",
        "zipkin.collector.scribe.port:9999"
    );
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinScribeCollectorAutoConfiguration.class, InMemoryConfiguration.class);
    context.refresh();

    assertThat(context.getBean(ZipkinScribeCollectorProperties.class).getPort())
        .isEqualTo(9999);
  }

  @Configuration
  static class InMemoryConfiguration {
    @Bean CollectorSampler sampler() {
      return CollectorSampler.ALWAYS_SAMPLE;
    }

    @Bean CollectorMetrics metrics() {
      return CollectorMetrics.NOOP_METRICS;
    }

    @Bean StorageComponent storage() {
      return new InMemoryStorage();
    }
  }
}
