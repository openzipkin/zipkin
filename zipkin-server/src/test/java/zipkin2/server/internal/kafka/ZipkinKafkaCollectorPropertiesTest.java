/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.kafka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZipkinKafkaCollectorPropertiesTest {
  @Test void stringPropertiesConvertEmptyStringsToNull() {
    final ZipkinKafkaCollectorProperties properties = new ZipkinKafkaCollectorProperties();
    properties.setBootstrapServers("");
    properties.setGroupId("");
    properties.setTopic("");
    assertThat(properties.getBootstrapServers()).isNull();
    assertThat(properties.getGroupId()).isNull();
    assertThat(properties.getTopic()).isNull();
  }
}
