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
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.ITZipkinServer.url;

/**
 * Integration test suite for CORS configuration.
 *
 * Verifies that allowed-origins can be configured via properties (zipkin.query.allowed-origins).
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    "zipkin.query.allowed-origins=" + ITZipkinServerCORS.ALLOWED_ORIGIN
  }
)
@RunWith(SpringRunner.class)
public class ITZipkinServerCORS {
  static final String ALLOWED_ORIGIN = "http://foo.example.com";
  static final String DISALLOWED_ORIGIN = "http://bar.example.com";

  @Autowired Server server;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  /** Notably, javascript makes pre-flight requests, and won't POST spans if disallowed! */
  @Test public void shouldAllowConfiguredOrigin_preflight() throws Exception {
    shouldPermitPreflight(optionsForOrigin("GET", "/api/v2/traces", ALLOWED_ORIGIN));
    shouldPermitPreflight(optionsForOrigin("POST", "/api/v2/spans", ALLOWED_ORIGIN));
  }

  static void shouldPermitPreflight(Response response) {
    assertThat(response.isSuccessful())
      .withFailMessage(response.toString())
      .isTrue();
    assertThat(response.header("vary")).contains("origin");
    assertThat(response.header("access-control-allow-origin")).contains(ALLOWED_ORIGIN);
    assertThat(response.header("access-control-allow-methods"))
      .contains(response.request().header("access-control-request-method"));
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-headers")).contains("content-type");
  }

  @Test public void shouldAllowConfiguredOrigin() throws Exception {
    shouldAllowConfiguredOrigin(getTracesFromOrigin(ALLOWED_ORIGIN));
    shouldAllowConfiguredOrigin(postSpansFromOrigin(ALLOWED_ORIGIN));
  }

  static void shouldAllowConfiguredOrigin(Response response) {
    assertThat(response.header("vary")).contains("origin");
    assertThat(response.header("access-control-allow-origin"))
      .contains(response.request().header("origin"));
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-headers")).contains("content-type");
  }

  @Test public void shouldDisallowOrigin() throws Exception {
    shouldDisallowOrigin(getTracesFromOrigin(DISALLOWED_ORIGIN));
    shouldDisallowOrigin(postSpansFromOrigin(DISALLOWED_ORIGIN));
  }

  static void shouldDisallowOrigin(Response response) {
    assertThat(response.header("vary")).isNull();
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-origin")).isNull();
    assertThat(response.header("access-control-allow-headers")).isNull();
  }

  private Response optionsForOrigin(String method, String path, String origin) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .header("Origin", origin)
      .header("access-control-request-method", method)
      .header("access-control-request-headers", "content-type")
      .method("OPTIONS", null)
      .build()).execute();
  }

  private Response getTracesFromOrigin(String origin) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/traces"))
      .header("Origin", origin)
      .build()).execute();
  }

  private Response postSpansFromOrigin(String origin) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .header("Origin", origin)
      .post(RequestBody.create("[]", MediaType.parse("application/json")))
      .build()).execute();
  }
}
