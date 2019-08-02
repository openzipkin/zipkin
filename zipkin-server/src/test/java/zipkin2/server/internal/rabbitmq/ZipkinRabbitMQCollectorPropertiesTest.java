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
package zipkin2.server.internal.rabbitmq;

import com.rabbitmq.client.ConnectionFactory;
import java.net.URI;
import java.util.Collections;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinRabbitMQCollectorPropertiesTest {
  ZipkinRabbitMQCollectorProperties properties = new ZipkinRabbitMQCollectorProperties();

  @Test public void uriProperlyParsedAndIgnoresOtherProperties_whenUriSet() throws Exception {
    properties.setUri(URI.create("amqp://admin:admin@localhost:5678/myv"));
    properties.setAddresses(Collections.singletonList("will_not^work!"));
    properties.setUsername("bob");
    properties.setPassword("letmein");
    properties.setVirtualHost("drwho");

    assertThat(properties.toBuilder())
      .extracting("connectionFactory")
      .satisfies(object -> {
        ConnectionFactory connFactory = (ConnectionFactory) object;
        assertThat(connFactory.getHost()).isEqualTo("localhost");
        assertThat(connFactory.getPort()).isEqualTo(5678);
        assertThat(connFactory.getUsername()).isEqualTo("admin");
        assertThat(connFactory.getPassword()).isEqualTo("admin");
        assertThat(connFactory.getVirtualHost()).isEqualTo("myv");
      });
  }

  /** This prevents an empty RABBIT_URI variable from being mistaken as a real one */
  @Test public void ignoresEmptyURI() {
    properties.setUri(URI.create(""));

    assertThat(properties.getUri()).isNull();
  }
}
