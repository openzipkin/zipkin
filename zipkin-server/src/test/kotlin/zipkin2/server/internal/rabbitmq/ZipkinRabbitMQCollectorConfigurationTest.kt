/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal.rabbitmq

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Ignore
import org.junit.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import zipkin2.collector.rabbitmq.RabbitMQCollector
import zipkin2.server.internal.InMemoryCollectorConfiguration

class ZipkinRabbitMQCollectorConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesNotProvideCollectorComponent_whenAddressAndUriNotSet() {
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinRabbitMQCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)
    context.refresh()

    context.getBean(RabbitMQCollector::class.java)
  }

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesNotProvideCollectorComponent_whenAddressesAndUriIsEmptyString() {
    TestPropertyValues.of(
      "zipkin.collector.rabbitmq.addresses:",
      "zipkin.collector.rabbitmq.uri:")
      .applyTo(context)
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinRabbitMQCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)
    context.refresh()

    context.getBean(RabbitMQCollector::class.java)
  }

  @Test @Ignore fun providesCollectorComponent_whenAddressesSet() {
    TestPropertyValues.of("zipkin.collector.rabbitmq.addresses=localhost:5672").applyTo(context)
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinRabbitMQCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)
    context.refresh()

    assertThat(context.getBean(RabbitMQCollector::class.java)).isNotNull
  }
}
