/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.collector.activemq.ActiveMQCollector;
import zipkin2.server.internal.InMemoryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class ZipkinActiveMQCollectorPropertiesTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  /** This prevents an empty ACTIVEMQ_URL variable from being mistaken as a real one */
  @Test public void ignoresEmptyURL() {
    ZipkinActiveMQCollectorProperties properties = new ZipkinActiveMQCollectorProperties();
    properties.setUrl("");

    assertThat(properties.getUrl()).isNull();
  }

  @Test public void providesCollectorComponent_whenUrlSet() {
    TestPropertyValues.of("zipkin.collector.activemq.url:tcp://localhost:61611") // wrong port
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinActiveMQCollectorConfiguration.class,
      InMemoryConfiguration.class);

    try {
      context.refresh();
      failBecauseExceptionWasNotThrown(BeanCreationException.class);
    } catch (BeanCreationException e) {
      assertThat(e.getCause()).hasMessageContaining(
        "Unable to establish connection to ActiveMQ broker: Connection refused");
    }
  }

  @Test public void doesNotProvidesCollectorComponent_whenUrlSetAndDisabled() {
    TestPropertyValues.of("zipkin.collector.activemq.url:tcp://localhost:61616")
      .applyTo(context);
    TestPropertyValues.of("zipkin.collector.activemq.enabled:false").applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinActiveMQCollectorConfiguration.class,
      InMemoryConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(ActiveMQCollector.class);
  }
}
