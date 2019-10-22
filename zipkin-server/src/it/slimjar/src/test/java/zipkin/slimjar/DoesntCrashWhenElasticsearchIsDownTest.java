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

public class DoesntCrashWhenElasticsearchIsDownTest {
  @Rule public ExecJarRule zipkin = new ExecJarRule()
      .putEnvironment("STORAGE_TYPE", "elasticsearch")
      .putEnvironment("ES_HOSTS", "127.0.0.1:9999");

  OkHttpClient client = new OkHttpClient();

  @Test public void startsButReturns500QueryingStorage() throws IOException {
    try {
      Response services = get("/api/v2/services");
      assertThat(services.code())
        .withFailMessage(services.body().string())
        .isEqualTo(500);
    } catch (RuntimeException | IOException e) {
      fail(String.format("unexpected error!%s%n%s", e.getMessage(), zipkin.consoleOutput()));
    }
  }

  @Test public void startsButReturnsFailedHealthCheck() throws IOException {
    try {
      assertThat(get("/health").code()).isEqualTo(503);
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
