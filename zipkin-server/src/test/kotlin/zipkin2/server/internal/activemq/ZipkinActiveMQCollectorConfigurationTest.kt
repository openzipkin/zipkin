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
package zipkin2.server.internal.activemq

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.After
import org.junit.Test
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import zipkin2.collector.activemq.ActiveMQCollector
import zipkin2.server.internal.InMemoryCollectorConfiguration

class ZipkinActiveMQCollectorConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesNotProvideCollectorComponent_whenAddressAndUriNotSet() {
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinActiveMQCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)
    context.refresh()

    context.getBean(ActiveMQCollector::class.java)
  }

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesNotProvideCollectorComponent_whenUrlIsEmptyString() {
    TestPropertyValues.of("zipkin.collector.activemq.uri:").applyTo(context)
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinActiveMQCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)
    context.refresh()

    context.getBean(ActiveMQCollector::class.java)
  }

  @Test fun providesCollectorComponent_whenUrlSet() {
    TestPropertyValues.of("zipkin.collector.activemq.url=vm://localhost").applyTo(context)
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinActiveMQCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)

    try {
      context.refresh()
      fail<String>("should have failed")
    } catch (e: BeanCreationException) {
      assertThat(e.cause).hasMessage(
        "Unable to establish connection to ActiveMQ broker: Transport scheme NOT recognized: [vm]")
    }
  }
}
