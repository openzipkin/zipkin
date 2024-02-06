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

import com.linecorp.armeria.common.auth.BasicToken;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZipkinEurekaDiscoveryPropertiesTest {
  @Test void stringPropertiesConvertEmptyStringsToNull() {
    ZipkinEurekaDiscoveryProperties properties = new ZipkinEurekaDiscoveryProperties();
    properties.setServiceUrl(URI.create(""));
    properties.setAppName("");
    properties.setInstanceId("");
    properties.setHostname("");
    assertThat(properties.getServiceUrl()).isNull();
    assertThat(properties.getAppName()).isNull();
    assertThat(properties.getInstanceId()).isNull();
    assertThat(properties.getHostname()).isNull();
    assertThat(properties).extracting("auth").isNull();
  }

  // Less logic to strip than fail on unlikely, but invalid input
  @Test void setServiceUrl_stripsQueryAndFragment() {
    ZipkinEurekaDiscoveryProperties properties = new ZipkinEurekaDiscoveryProperties();
    properties.setServiceUrl(URI.create("http://localhost:8761/eureka/v2?q1=v1#toc"));

    assertThat(properties.getServiceUrl())
      .isEqualTo(URI.create("http://localhost:8761/eureka/v2"));
    assertThat(properties).extracting("auth").isNull();
  }

  @Test void setServiceUrl_extractsBasicToken() {
    ZipkinEurekaDiscoveryProperties properties = new ZipkinEurekaDiscoveryProperties();
    properties.setServiceUrl(URI.create("http://myuser:mypassword@localhost:8761/eureka/v2"));

    assertThat(properties.getServiceUrl())
      .isEqualTo(URI.create("http://localhost:8761/eureka/v2"));
    assertThat(properties).extracting("auth")
      .isEqualTo(BasicToken.ofBasic("myuser", "mypassword"));
  }
}
