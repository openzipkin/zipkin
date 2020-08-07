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
package zipkin2.server.internal.health;

import com.jayway.jsonpath.JsonPath;
import com.linecorp.armeria.server.Server;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
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
public class ITZipkinHealth {
  @Autowired InMemoryStorage storage;
  @Autowired PrometheusMeterRegistry registry;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @Before public void init() {
    storage.clear();
  }

  @Test public void healthIsOK() throws Exception {
    Response health = get("/health");
    assertThat(health.isSuccessful()).isTrue();
    assertThat(health.body().contentType())
      .hasToString("application/json; charset=utf-8");
    assertThat(health.body().string()).isEqualTo(""
      + "{\n"
      + "  \"status\" : \"UP\",\n"
      + "  \"zipkin\" : {\n"
      + "    \"status\" : \"UP\",\n"
      + "    \"details\" : {\n"
      + "      \"InMemoryStorage{}\" : {\n"
      + "        \"status\" : \"UP\"\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "}"
    );

    // ensure we don't track health in prometheus
    assertThat(scrape())
      .doesNotContain("health");
  }

  String scrape() throws InterruptedException {
    Thread.sleep(100);
    return registry.scrape();
  }

  @Test public void readsHealth() throws Exception {
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
