/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.pulsar;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ZipkinPulsarCollectorPropertiesTest {
  @Test void stringPropertiesConvertEmptyStringsToNull() {
    final ZipkinPulsarCollectorProperties properties = new ZipkinPulsarCollectorProperties();
    properties.setServiceUrl("pulsar://127.0.0.1:6650");
    properties.setTopic("zipkin");
    properties.setClientProps(new HashMap<>());
    properties.setConsumerProps(new HashMap<>());
    assertThat(properties.getServiceUrl()).isEqualTo("pulsar://127.0.0.1:6650");
    assertThat(properties.getTopic()).isEqualTo("zipkin");
    assertThat(properties.getClientProps()).isEmpty();
    assertThat(properties.getConsumerProps()).isEmpty();
  }
}
