/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.io.IOException;
import java.io.InterruptedIOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static zipkin2.server.internal.ITZipkinServer.url;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server"
  }
)
@RunWith(SpringRunner.class)
public class ITActuatorMappings {
  @Autowired PrometheusMeterRegistry registry;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @Test public void actuatorIsOK() throws Exception {
    assumeThat(get("/actuator").isSuccessful()) // actuator is optional
      .isTrue();

    // ensure we don't track actuator in prometheus
    assertThat(scrape())
      .doesNotContain("actuator");
  }

  @Test public void actuatorInfoEndpointHasDifferentContentType() throws IOException {
    Response info = get("/info");
    Response actuatorInfo = get("/actuator/info");

    // Different content type
    assertThat(actuatorInfo.isSuccessful()).isTrue();
    assertThat(actuatorInfo.body().contentType())
      .isNotEqualTo(info.body().contentType())
      .hasToString("application/vnd.spring-boot.actuator.v2+json; charset=utf-8");

    // Same content
    assertThat(actuatorInfo.body().string())
      .isEqualTo(info.body().string());

    // ensure we don't track info in prometheus
    assertThat(scrape())
      .doesNotContain("/info");
  }

  @Test public void actuatorHealthEndpointHasDifferentContentType() throws IOException {
    Response health = get("/health");
    Response actuatorHealth = get("/actuator/health");

    // Different content type
    assertThat(actuatorHealth.isSuccessful()).isTrue();
    assertThat(actuatorHealth.body().contentType())
      .isNotEqualTo(health.body().contentType())
      .hasToString("application/vnd.spring-boot.actuator.v2+json; charset=utf-8");

    // Same content
    assertThat(actuatorHealth.body().string())
      .isEqualTo(health.body().string());

    // ensure we don't track health in prometheus
    assertThat(scrape())
      .doesNotContain("/health");
  }

  Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .build()).execute();
  }

  String scrape() throws IOException {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      throw new InterruptedIOException(e.getMessage());
    }
    return registry.scrape();
  }
}
