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
package zipkin2.server.internal.activemq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.collector.activemq.ActiveMQCollector;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ZipkinActiveMQCollectorConfigurationTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach public void close() {
    context.close();
  }

  @Test void doesNotProvideCollectorComponent_whenAddressAndUriNotSet() {
    assertThrows(NoSuchBeanDefinitionException.class, () -> {
      context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinActiveMQCollectorConfiguration.class,
        InMemoryConfiguration.class);
      context.refresh();
      context.getBean(ActiveMQCollector.class);
    });
  }

  @Test void providesCollectorComponent_whenUrlSet() {
    TestPropertyValues.of("zipkin.collector.activemq.url=vm://localhost")
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinActiveMQCollectorConfiguration.class,
      InMemoryConfiguration.class);

    try {
      context.refresh();
      failBecauseExceptionWasNotThrown(BeanCreationException.class);
    } catch (BeanCreationException e) {
      assertThat(e.getCause()).hasMessage(
        "Unable to establish connection to ActiveMQ broker: Transport scheme NOT recognized: [vm]");
    }
  }
}
