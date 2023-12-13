/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
package zipkin2.server.internal.rabbitmq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.collector.rabbitmq.RabbitMQCollector;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class ZipkinRabbitMQCollectorConfigurationTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach void close() {
    context.close();
  }

  @Test void doesNotProvideCollectorComponent_whenAddressAndUriNotSet() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(RabbitMQCollector.class);
    });
  }

  @Test void doesNotProvideCollectorComponent_whenAddressesAndUriIsEmptyString() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context = new AnnotationConfigApplicationContext();
      TestPropertyValues.of(
        "zipkin.collector.rabbitmq.addresses:",
        "zipkin.collector.rabbitmq.uri:")
        .applyTo(context);
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(RabbitMQCollector.class);
    });
  }

  @Test void providesCollectorComponent_whenAddressesSet() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of("zipkin.collector.rabbitmq.addresses:localhost:1234").applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinRabbitMQCollectorConfiguration.class,
      InMemoryConfiguration.class);

    try {
      context.refresh();
      failBecauseExceptionWasNotThrown(BeanCreationException.class);
    } catch (BeanCreationException e) {
      assertThat(e.getCause()).hasMessageContaining(
        "Unable to establish connection to RabbitMQ server: Connection refused");
    }
  }

  @Test void doesNotProvidesCollectorComponent_whenAddressesSetAndDisabled() {
    assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> {
      context = new AnnotationConfigApplicationContext();
      TestPropertyValues.of("zipkin.collector.rabbitmq.addresses:localhost:1234").applyTo(context);
      TestPropertyValues.of("zipkin.collector.rabbitmq.enabled:false").applyTo(context);
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinRabbitMQCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(RabbitMQCollector.class);
    });
  }
}
