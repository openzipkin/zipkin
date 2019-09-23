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
package zipkin.slimjar;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class AdminEndpointsTest {
  @Rule public ExecJarRule zipkin = new ExecJarRule();

  OkHttpClient client = new OkHttpClient();

  /** Tests admin endpoints work eventhough actuator is not a strict dependency. */
  @Test public void adminEndpoints() throws Exception {
    try {
      // Documented as supported in our zipkin-server/README.md
      assertThat(get("/health").isSuccessful()).isTrue();
      assertThat(get("/info").isSuccessful()).isTrue();
      assertThat(get("/metrics").isSuccessful()).isTrue();
      assertThat(get("/prometheus").isSuccessful()).isTrue();

      // Check endpoints we formerly redirected to. Note we never redirected to /actuator/metrics
      assertThat(get("/actuator/health").isSuccessful()).isTrue();
      assertThat(get("/actuator/info").isSuccessful()).isTrue();
      assertThat(get("/actuator/prometheus").isSuccessful()).isTrue();
    } catch (RuntimeException | IOException e) {
      fail(String.format("unexpected error!%s%n%s", e.getMessage(), zipkin.consoleOutput()));
    }
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkin.port() + path)
      .build()).execute();
  }
}
