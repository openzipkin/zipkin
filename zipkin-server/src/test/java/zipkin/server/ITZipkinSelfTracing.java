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
package zipkin.server;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "spring.config.name=zipkin-server",
    "zipkin.self-tracing.enabled=true"
  }
)
@RunWith(SpringRunner.class)
public class ITZipkinSelfTracing {
  @LocalServerPort int zipkinPort;
  @Autowired AsyncReporter<Span> reporter;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Test public void queryRelatedEndpoints404() throws Exception {
    assertThat(get("/api/v2/traces").code()).isEqualTo(200);

    Thread.sleep(1000);

    assertThat(get("/api/v2/services").body().string())
      .isEqualTo("[\"zipkin-server\"]");
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + path)
      .build()).execute();
  }
}
