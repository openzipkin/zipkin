/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
package zipkin2.server.internal.eureka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZipkinEurekaDiscoveryPropertiesTest {
  @Test void stringPropertiesConvertEmptyStringsToNull() {
    final ZipkinEurekaDiscoveryProperties properties = new ZipkinEurekaDiscoveryProperties();
    properties.setServiceUrl("");
    properties.setAppName("");
    properties.setInstanceId("");
    properties.setHostname("");
    assertThat(properties.getServiceUrl()).isNull();
    assertThat(properties.getAppName()).isNull();
    assertThat(properties.getInstanceId()).isNull();
    assertThat(properties.getHostname()).isNull();
  }
}
