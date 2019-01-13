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
package zipkin2.autoconfigure.ui;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = TestServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class ITZipkinUiAutoConfiguration {

  @Autowired Server server;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  /** The zipkin-ui is a single-page app. This prevents reloading all resources on each click. */
  @Test public void setsMaxAgeOnUiResources() throws Exception {
    assertThat(get("/zipkin/config.json").header("Cache-Control"))
      .isEqualTo("max-age=600");
    assertThat(get("/zipkin/index.html").header("Cache-Control"))
      .isEqualTo("max-age=60");
    assertThat(get("/zipkin/test.txt").header("Cache-Control"))
      .isEqualTo("max-age=31536000");
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + server.activePort().get().localAddress().getPort() + path)
      .build()).execute();
  }
}
