/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.health;

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

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    "zipkin.storage.type=elasticsearch",
    "zipkin.storage.elasticsearch.hosts=127.0.0.1:9999"
  }
)
class ITZipkinHealthDown {
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @Test void downHasCorrectCode() throws Exception {
    Response check = get("/health");
    assertThat(check.code()).isEqualTo(503);
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder().url(url(server, path)).build()).execute();
  }
}
