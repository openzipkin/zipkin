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
package zipkin2.server.internal

import brave.Tracing
import com.linecorp.armeria.spring.actuate.ArmeriaSpringActuatorAutoConfiguration
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown
import org.junit.After
import org.junit.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration
import org.springframework.boot.actuate.health.HealthAggregator
import org.springframework.boot.actuate.health.OrderedHealthAggregator
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import zipkin2.server.internal.brave.TracingConfiguration
import zipkin2.storage.StorageComponent

class ZipkinServerConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test fun httpCollector_enabledByDefault() {
    context.register(
      ArmeriaSpringActuatorAutoConfiguration::class.java,
      EndpointAutoConfiguration::class.java,
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinServerConfigurationTest.Config::class.java,
      ZipkinServerConfiguration::class.java,
      ZipkinHttpCollector::class.java
    )
    context.refresh()

    assertThat(context.getBean(ZipkinHttpCollector::class.java)).isNotNull
  }

  @Test(expected = NoSuchBeanDefinitionException::class)
  fun httpCollector_canDisable() {
    TestPropertyValues.of("zipkin.collector.http.enabled:false").applyTo(context)
    context.register(
      ArmeriaSpringActuatorAutoConfiguration::class.java,
      EndpointAutoConfiguration::class.java,
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinServerConfigurationTest.Config::class.java,
      ZipkinServerConfiguration::class.java,
      ZipkinHttpCollector::class.java
    )
    context.refresh()

    context.getBean(ZipkinHttpCollector::class.java)
  }

  @Test fun query_enabledByDefault() {
    context.register(
      ArmeriaSpringActuatorAutoConfiguration::class.java,
      EndpointAutoConfiguration::class.java,
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinServerConfigurationTest.Config::class.java,
      ZipkinServerConfiguration::class.java,
      ZipkinQueryApiV2::class.java
    )
    context.refresh()

    assertThat(context.getBean(ZipkinQueryApiV2::class.java)).isNotNull
  }

  @Test fun query_canDisable() {
    TestPropertyValues.of("zipkin.query.enabled:false").applyTo(context)
    context.register(
      ArmeriaSpringActuatorAutoConfiguration::class.java,
      EndpointAutoConfiguration::class.java,
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinServerConfigurationTest.Config::class.java,
      ZipkinServerConfiguration::class.java,
      ZipkinQueryApiV2::class.java
    )
    context.refresh()

    try {
      context.getBean(ZipkinQueryApiV2::class.java)
      failBecauseExceptionWasNotThrown<Any>(NoSuchBeanDefinitionException::class.java)
    } catch (e: NoSuchBeanDefinitionException) {
    }

  }

  @Test fun selfTracing_canEnable() {
    TestPropertyValues.of("zipkin.self-tracing.enabled:true").applyTo(context)
    context.register(
      ArmeriaSpringActuatorAutoConfiguration::class.java,
      EndpointAutoConfiguration::class.java,
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinServerConfigurationTest.Config::class.java,
      ZipkinServerConfiguration::class.java,
      TracingConfiguration::class.java
    )
    context.refresh()

    context.getBean(Tracing::class.java).close()
  }

  @Test fun search_canDisable() {
    TestPropertyValues.of("zipkin.storage.search-enabled:false").applyTo(context)
    context.register(
      ArmeriaSpringActuatorAutoConfiguration::class.java,
      EndpointAutoConfiguration::class.java,
      PropertyPlaceholderAutoConfiguration::class.java,
      ZipkinServerConfigurationTest.Config::class.java,
      ZipkinServerConfiguration::class.java
    )
    context.refresh()

    val v2Storage = context.getBean(StorageComponent::class.java)
    assertThat(v2Storage)
      .extracting("searchEnabled")
      .containsExactly(false)
  }

  @Configuration
  open class Config {
    @Bean open fun healthAggregator(): HealthAggregator {
      return OrderedHealthAggregator()
    }

    @Bean open fun registry(): MeterRegistry {
      return PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    }
  }
}
