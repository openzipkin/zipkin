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

class ITZipkinEurekaAuthenticated extends BaseITZipkinEureka {
  static final String username = "user", password = "pass";
  @Container static EurekaContainer eureka =
    new EurekaContainer(Map.of("EUREKA_USERNAME", username, "EUREKA_PASSWORD", password));

  static HttpUrl serviceUrl() {
    return eureka.serviceUrl().newBuilder().username(username).password(password).build();
  }

  /** Get the serviceUrl of the Eureka container prior to booting Zipkin. */
  @DynamicPropertySource static void propertyOverride(DynamicPropertyRegistry registry) {
    registry.add("zipkin.discovery.eureka.serviceUrl", () -> serviceUrl().url());
  }

  ITZipkinEurekaAuthenticated() {
    super(serviceUrl());
  }
}
