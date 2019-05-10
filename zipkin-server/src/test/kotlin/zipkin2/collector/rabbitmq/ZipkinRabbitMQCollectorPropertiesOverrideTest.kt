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
package zipkin2.collector.rabbitmq

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import zipkin2.server.internal.rabbitmq.Access
import java.net.URI

@RunWith(Parameterized::class)
class ZipkinRabbitMQPropertiesOverrideTest(
  val property: String,
  val value: Any,
  val builderExtractor: (RabbitMQCollector.Builder) -> Any
) {

  companion object {
    @JvmStatic @Parameterized.Parameters fun data(): List<Array<Any?>> {
      return listOf(
        // intentionally punting on comma-separated form of a list of addresses as it doesn't fit
        // this unit test. Better to make a separate one than force-fit!
        parameters("addresses", "localhost:5671",
          { builder -> builder.addresses[0].toString() }),
        parameters("concurrency", 2,
          { builder -> builder.concurrency }),
        parameters("connectionTimeout", 30000,
          { builder -> builder.connectionFactory.connectionTimeout }),
        parameters("password", "admin",
          { builder -> builder.connectionFactory.password }),
        parameters("queue", "zapkin",
          { builder -> builder.queue }),
        parameters("username", "admin",
          { builder -> builder.connectionFactory.username }),
        parameters("virtualHost", "/hello",
          { builder -> builder.connectionFactory.virtualHost }),
        parameters("useSsl", true,
          { builder -> builder.connectionFactory.isSSL }),
        parameters("uri", URI.create("amqp://localhost"),
          { builder -> URI.create("amqp://" + builder.connectionFactory.host) })
      )
    }

    /** to allow us to define with a lambda  */
    internal fun <T> parameters(
      propertySuffix: String, value: T, builderExtractor: (RabbitMQCollector.Builder) -> T
    ): Array<Any?> = arrayOf(propertySuffix, value, builderExtractor)
  }

  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test fun propertyTransferredToCollectorBuilder() {
    TestPropertyValues.of("zipkin.collector.rabbitmq.$property:$value").applyTo(context)
    Access.registerRabbitMQProperties(context)
    context.refresh()

    assertThat(Access.collectorBuilder(context))
      .extracting(builderExtractor)
      .isEqualTo(value)
  }
}
