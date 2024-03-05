/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.health;

import com.jayway.jsonpath.JsonPath;
import com.linecorp.armeria.server.Server;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.ITZipkinServer.url;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
  }
)
class ITZipkinHealth {
  @Autowired InMemoryStorage storage;
  @Autowired PrometheusMeterRegistry registry;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @BeforeEach void init() {
    storage.clear();
  }

  @Test void healthIsOK() throws Exception {
    Response health = get("/health");
    assertThat(health.isSuccessful()).isTrue();
    assertThat(health.body().contentType())
      .hasToString("application/json; charset=utf-8");
    assertThat(health.body().string()).isEqualTo("""
      {
        "status" : "UP",
        "zipkin" : {
          "status" : "UP",
          "details" : {
            "InMemoryStorage{}" : {
              "status" : "UP"
            }
          }
        }
      }"""
    );

    // ensure we don't track health in prometheus
    assertThat(scrape())
      // "application_ready_time_seconds" includes this test's full class name
      // which includes its package (named health). We care about the endpoint
      // /health not being in the results, so check for that here.
      .doesNotContain("/health");
  }

  String scrape() throws InterruptedException {
    Thread.sleep(100);
    return registry.scrape();
  }

  @Test void readsHealth() throws Exception {
    String json = getAsString("/health");
    assertThat(readString(json, "$.status"))
      .isIn("UP", "DOWN", "UNKNOWN");
    assertThat(readString(json, "$.zipkin.status"))
      .isIn("UP", "DOWN", "UNKNOWN");
  }

  private String getAsString(String path) throws IOException {
    Response response = get(path);
    assertThat(response.isSuccessful())
      .withFailMessage(response.toString())
      .isTrue();
    return response.body().string();
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder().url(url(server, path)).build()).execute();
  }

  static String readString(String json, String jsonPath) {
    return JsonPath.compile(jsonPath).read(json);
  }
}
