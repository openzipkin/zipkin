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
package zipkin2.server.internal.kafka

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import zipkin2.collector.kafka.KafkaCollector
import zipkin2.server.internal.InMemoryCollectorConfiguration

class ZipkinKafkaCollectorConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun doesNotProvideCollectorComponent_whenBootstrapServersUnset() {
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinKafkaCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)
    context.refresh()

    context.getBean(KafkaCollector::class.java)
  }

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun providesCollectorComponent_whenBootstrapServersEmptyString() {
    TestPropertyValues.of("zipkin.collector.kafka.bootstrap-servers:").applyTo(context)
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinKafkaCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)
    context.refresh()

    context.getBean(KafkaCollector::class.java)
  }

  @Test fun providesCollectorComponent_whenBootstrapServersSet() {
    TestPropertyValues.of("zipkin.collector.kafka.bootstrap-servers:localhost:9091")
      .applyTo(context)
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinKafkaCollectorConfiguration::class.java,
      InMemoryCollectorConfiguration::class.java)
    context.refresh()

    assertThat(context.getBean(KafkaCollector::class.java)).isNotNull
  }
}
