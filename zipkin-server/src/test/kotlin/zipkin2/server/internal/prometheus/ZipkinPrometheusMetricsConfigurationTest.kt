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
package zipkin2.server.internal.prometheus

import com.linecorp.armeria.spring.ArmeriaServerConfigurator
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class ZipkinPrometheusMetricsConfigurationTest {
  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test fun providesHttpRequestDurationCustomizer() {
    refresh()

    context.getBeansOfType(ArmeriaServerConfigurator::class.java)
  }

  @Test fun defaultMetricName() {
    refresh()

    assertThat(context.getBean(ZipkinPrometheusMetricsConfiguration::class.java).metricName)
      .isEqualTo("http.server.requests")
  }

  @Test fun overrideMetricName() {
    TestPropertyValues.of("management.metrics.web.server.requests-metric-name:foo").applyTo(context)
    refresh()

    assertThat(context.getBean(ZipkinPrometheusMetricsConfiguration::class.java).metricName)
      .isEqualTo("foo")
  }

  fun refresh() {
    context.register(
      PropertyPlaceholderAutoConfiguration::class.java,
      MetricsAutoConfiguration::class.java,
      PrometheusMetricsExportAutoConfiguration::class.java,
      ZipkinPrometheusMetricsConfiguration::class.java
    )
    context.refresh()
  }
}
