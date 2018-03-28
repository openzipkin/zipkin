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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.storage.InMemoryStorage;

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
  @Value("${local.server.port}") int zipkinPort;
  @Autowired InMemoryStorage storage;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Before public void clear() {
    storage.clear();
  }

  @Test public void getIsTraced_v1() throws Exception {
    assertThat(get("v1").body().string())
      .isEqualTo("[]");

    Thread.sleep(1000);

    assertThat(get("v1").body().string())
      .isEqualTo("[\"zipkin-server\"]");
  }

  @Test public void getIsTraced_v2() throws Exception {
    assertThat(get("v2").body().string())
      .isEqualTo("[]");

    Thread.sleep(1000);

    assertThat(get("v2").body().string())
      .isEqualTo("[\"zipkin-server\"]");
  }

  @Test public void postIsTraced_v1() throws Exception {
    post("v1");

    Thread.sleep(1000);

    assertThat(get("v1").body().string())
      .isEqualTo("[\"zipkin-server\"]");
  }

  @Test public void postIsTraced_v2() throws Exception {
    post("v2");

    Thread.sleep(1000);

    assertThat(get("v2").body().string())
      .isEqualTo("[\"zipkin-server\"]");
  }

  private void post(String version) throws IOException {
    client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/" + version + "/spans")
      .header("x-b3-sampled", "1") // we don't trace POST by default
      .post(RequestBody.create(null, "[" + "]"))
      .build()).execute();
  }

  private Response get(String version) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/" + version + "/services")
      .build()).execute();
  }
}
