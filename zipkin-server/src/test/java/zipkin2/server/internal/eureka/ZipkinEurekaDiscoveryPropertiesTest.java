/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
