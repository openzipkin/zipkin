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
