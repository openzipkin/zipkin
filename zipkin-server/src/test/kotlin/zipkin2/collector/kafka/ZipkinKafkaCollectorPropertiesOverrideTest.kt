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
package zipkin2.collector.kafka

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import zipkin2.server.internal.kafka.Access

@RunWith(Parameterized::class)
class ZipkinKafkaCollectorPropertiesOverrideTest(
  val property: String,
  val value: Any,
  val builderExtractor: (KafkaCollector.Builder) -> Any
) {

  companion object {
    @JvmStatic @Parameterized.Parameters fun data(): List<Array<Any?>> {
      return listOf(
        parameters("bootstrap-servers", "127.0.0.1:9092",
          { b -> b.properties.getProperty("bootstrap.servers") }),
        parameters("group-id", "zapkin",
          { b -> b.properties.getProperty("group.id") }),
        parameters("topic", "zapkin",
          { b -> b.topic }),
        parameters("streams", 2,
          { b -> b.streams }),
        parameters("overrides.auto.offset.reset", "latest",
          { b -> b.properties.getProperty("auto.offset.reset") })
      )
    }

    /** to allow us to define with a lambda  */
    internal fun <T> parameters(
      propertySuffix: String, value: T, builderExtractor: (KafkaCollector.Builder) -> T
    ): Array<Any?> = arrayOf(propertySuffix, value, builderExtractor)
  }

  val context = AnnotationConfigApplicationContext()
  @After fun closeContext() = context.close()

  @Test fun propertyTransferredToCollectorBuilder() {
    TestPropertyValues.of("zipkin.collector.kafka.$property:$value").applyTo(context)
    Access.registerKafkaProperties(context)
    context.refresh()

    assertThat(Access.collectorBuilder(context))
      .extracting(builderExtractor)
      .isEqualTo(value)
  }
}
