/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.collector.kafka10;

import org.junit.Test;
import zipkin.autoconfigure.collector.kafka10.ZipkinKafkaCollectorProperties;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinKafkaCollectorPropertiesTest {
  @Test
  public void stringPropertiesConvertEmptyStringsToNull() throws Exception {
    final ZipkinKafkaCollectorProperties properties = new ZipkinKafkaCollectorProperties();
    properties.setBootstrapServers("");
    properties.setGroupId("");
    properties.setTopic("");
    assertThat(properties.getBootstrapServers()).isNull();
    assertThat(properties.getGroupId()).isNull();
    assertThat(properties.getTopic()).isNull();
  }
}
