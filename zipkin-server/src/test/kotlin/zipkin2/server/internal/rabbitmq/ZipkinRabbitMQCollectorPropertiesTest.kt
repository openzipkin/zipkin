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

import com.rabbitmq.client.ConnectionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URI

class ZipkinRabbitMQCollectorPropertiesTest {

  @Test fun uriProperlyParsedAndIgnoresOtherProperties_whenUriSet() {
    val properties = ZipkinRabbitMQCollectorProperties()
    properties.uri = URI.create("amqp://admin:admin@localhost:5678/myv")
    properties.addresses = listOf("will_not^work!")
    properties.username = "bob"
    properties.password = "letmein"
    properties.virtualHost = "drwho"

    assertThat(properties.toBuilder())
      .extracting("connectionFactory")
      .allSatisfy { `object` ->
        val connFactory = `object` as ConnectionFactory
        assertThat(connFactory.host).isEqualTo("localhost")
        assertThat(connFactory.port).isEqualTo(5678)
        assertThat(connFactory.username).isEqualTo("admin")
        assertThat(connFactory.password).isEqualTo("admin")
        assertThat(connFactory.virtualHost).isEqualTo("myv")
      }
  }

  /** This prevents an empty RABBIT_URI variable from being mistaken as a real one  */
  @Test fun ignoresEmptyURI() {
    val properties = ZipkinRabbitMQCollectorProperties()
    properties.uri = URI.create("")

    assertThat(properties.uri).isNull()
  }
}
