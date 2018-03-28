/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin.server.internal;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test suite for CORS configuration.
 *
 * Verifies that allowed-origins can be configured via properties (zipkin.query.allowed-origins).
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "spring.config.name=zipkin-server",
    "zipkin.query.allowed-origins=" + ITZipkinServerCORS.ALLOWED_ORIGIN
  }
)
@RunWith(SpringRunner.class)
public class ITZipkinServerCORS {
  static final String ALLOWED_ORIGIN = "http://foo.example.com";
  static final String DISALLOWED_ORIGIN = "http://bar.example.com";

  @Value("${local.server.port}") int zipkinPort;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  /** Notably, javascript makes pre-flight requests, and won't POST spans if disallowed! */
  @Test public void shouldAllowConfiguredOrigin_preflight() throws Exception {
    shouldPermitPreflight(optionsForOrigin("/api/v2/traces", ALLOWED_ORIGIN));
    shouldPermitPreflight(optionsForOrigin("/api/v2/spans", ALLOWED_ORIGIN));
  }

  static void shouldPermitPreflight(Response response) {
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.header("vary")).contains("origin");
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-origin")).contains(ALLOWED_ORIGIN);
  }

  @Test public void shouldAllowConfiguredOrigin() throws Exception {
    shouldAllowConfiguredOrigin(getTracesFromOrigin(ALLOWED_ORIGIN));
    shouldAllowConfiguredOrigin(postSpansFromOrigin(ALLOWED_ORIGIN));
  }

  static void shouldAllowConfiguredOrigin(Response response) {
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.header("vary")).contains("origin");
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-origin")).contains(ALLOWED_ORIGIN);
  }

  @Test public void shouldDisallowOrigin() throws Exception {
    shouldDisallowOrigin(getTracesFromOrigin(DISALLOWED_ORIGIN));
    shouldDisallowOrigin(postSpansFromOrigin(DISALLOWED_ORIGIN));
  }

  static void shouldDisallowOrigin(Response response) {
    assertThat(response.code()).isEqualTo(403);
    assertThat(response.header("vary")).isEqualTo("origin");
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-origin")).isNull();
  }

  private Response optionsForOrigin(String path, String origin) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + path)
      .header("Origin", origin)
      .method("OPTIONS", null)
      .build()).execute();
  }

  private Response getTracesFromOrigin(String origin) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/v2/traces")
      .header("Origin", origin)
      .build()).execute();
  }

  private Response postSpansFromOrigin(String origin) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/v2/spans")
      .header("Origin", origin)
      .post(RequestBody.create(null, "[]"))
      .build()).execute();
  }
}
