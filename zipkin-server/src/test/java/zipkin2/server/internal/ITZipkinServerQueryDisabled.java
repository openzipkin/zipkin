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
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.ITZipkinServer.url;

/**
 * Collector-only builds should be able to disable the query (and indirectly the UI), so that
 * associated assets 404 vs throw exceptions.
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    "zipkin.query.enabled=false",
    "zipkin.ui.enabled=false"
  }
)
class ITZipkinServerQueryDisabled {
  @Autowired Server server;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Test void queryRelatedEndpoints404() throws Exception {
    assertThat(get("/api/v2/traces").code()).isEqualTo(404);
    assertThat(get("/index.html").code()).isEqualTo(404);

    // but other endpoints are ok
    assertThat(get("/health").isSuccessful()).isTrue();
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder().url(url(server, path)).build()).execute();
  }
}
