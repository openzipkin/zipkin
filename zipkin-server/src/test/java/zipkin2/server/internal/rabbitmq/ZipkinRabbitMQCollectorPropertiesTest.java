/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.rabbitmq;

import com.rabbitmq.client.ConnectionFactory;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZipkinRabbitMQCollectorPropertiesTest {
  ZipkinRabbitMQCollectorProperties properties = new ZipkinRabbitMQCollectorProperties();

  @Test void uriProperlyParsedAndIgnoresOtherProperties_whenUriSet() throws Exception {
    properties.setUri(URI.create("amqp://admin:admin@localhost:5678/myv"));
    properties.setAddresses(List.of("will_not^work!"));
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
  @Test void ignoresEmptyURI() {
    properties.setUri(URI.create(""));

    assertThat(properties.getUri()).isNull();
  }
}
