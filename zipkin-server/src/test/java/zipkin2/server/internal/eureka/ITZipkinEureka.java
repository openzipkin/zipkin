/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.eureka;

import java.util.Map;
import okhttp3.HttpUrl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;

class ITZipkinEureka extends BaseITZipkinEureka {
  @Container static EurekaContainer eureka = new EurekaContainer(Map.of());

  static HttpUrl serviceUrl() {
    return eureka.serviceUrl();
  }

  /** Get the serviceUrl of the Eureka container prior to booting Zipkin. */
  @DynamicPropertySource static void propertyOverride(DynamicPropertyRegistry registry) {
    registry.add("zipkin.discovery.eureka.serviceUrl", () -> serviceUrl().url());
  }

  ITZipkinEureka() {
    super(serviceUrl());
  }
}
