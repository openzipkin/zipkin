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
package zipkin.collector.rabbitmq;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.autoconfigure.collector.rabbitmq.ZipkinRabbitMQCollectorAutoConfiguration;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.CollectorSampler;
import zipkin.storage.InMemoryStorage;
import zipkin.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinRabbitMQCollectorAutoConfigurationTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private AnnotationConfigApplicationContext context;

  @After
  public void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void doesNotProvideCollectorComponent_whenAddressNotSet() {
    context = new AnnotationConfigApplicationContext();
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorAutoConfiguration.class, InMemoryConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(RabbitMQCollector.class);
  }

  @Test
  public void doesNotProvideCollectorComponent_whenAddressesIsEmptyString() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.collector.rabbitmq.addresses:");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorAutoConfiguration.class, InMemoryConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(RabbitMQCollector.class);
  }

  @Test
  @Ignore
  public void providesCollectorComponent_whenAddressesSet() {
    context = new AnnotationConfigApplicationContext();
    addEnvironment(context, "zipkin.collector.rabbitmq.addresses=localhost:5672");
    context.register(PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorAutoConfiguration.class, InMemoryConfiguration.class);
    context.refresh();

    assertThat(context.getBean(RabbitMQCollector.class)).isNotNull();
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
