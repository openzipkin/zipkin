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
package zipkin2.collector.activemq

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import zipkin2.server.internal.activemq.Access

@RunWith(Parameterized::class)
class ZipkinActiveMQPropertiesOverrideTest(
  val property: String,
  val value: Any,
  val builderExtractor: (ActiveMQCollector.Builder) -> Any
) {

  companion object {
    @JvmStatic @Parameterized.Parameters fun data(): List<Array<Any?>> {
      return listOf(
        parameters("url", "failover:(tcp://localhost:61616,tcp://remotehost:61616)",
          { builder -> builder.connectionFactory.brokerURL.toString() }),
        parameters("concurrency", 2,
          { builder -> builder.concurrency }),
        parameters("queue", "zapkin",
          { builder -> builder.queue }),
        parameters("client-id-prefix", "zipkin-prod",
          { builder -> builder.connectionFactory.clientIDPrefix }),
        parameters("username", "u",
          { builder -> builder.connectionFactory.userName }),
        parameters("password", "p",
          { builder -> builder.connectionFactory.password })
      )
    }

    /** to allow us to define with a lambda  */
    internal fun <T> parameters(
      propertySuffix: String, value: T, builderExtractor: (ActiveMQCollector.Builder) -> T
    ): Array<Any?> = arrayOf(propertySuffix, value, builderExtractor)
  }

  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test fun propertyTransferredToCollectorBuilder() {
    if (property != "url") {
      TestPropertyValues.of("zipkin.collector.activemq.url:tcp://localhost:61616").applyTo(context)
    }

    TestPropertyValues.of("zipkin.collector.activemq.$property:$value").applyTo(context)

    if (property == "username") {
      TestPropertyValues.of("zipkin.collector.activemq.password:p").applyTo(context)
    }

    if (property == "password") {
      TestPropertyValues.of("zipkin.collector.activemq.username:u").applyTo(context)
    }

    Access.registerActiveMQProperties(context)
    context.refresh()

    assertThat(Access.collectorBuilder(context))
      .extracting(builderExtractor)
      .isEqualTo(value)
  }
}
