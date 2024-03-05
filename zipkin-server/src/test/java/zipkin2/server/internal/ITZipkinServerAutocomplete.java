/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.server.internal.ITZipkinServer.url;

/**
 * Integration test suite for autocomplete tags.
 * <p>
 * Verifies that the whitelist of key can be configured via "zipkin.storage.autocomplete-keys".
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    "zipkin.storage.autocomplete-keys=environment,clnt/finagle.version"
  }
)
class ITZipkinServerAutocomplete {

  @Autowired Server server;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Test void setsCacheControlOnAutocompleteKeysEndpoint() throws Exception {
    assertThat(get("/api/v2/autocompleteKeys").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");
  }

  @Test void setsCacheControlOnAutocompleteEndpointWhenMoreThan3Values() throws Exception {
    assertThat(get("/api/v2/autocompleteValues?key=environment").header("Cache-Control"))
      .isNull();
    assertThat(get("/api/v2/autocompleteValues?key=clnt/finagle.version").header("Cache-Control"))
      .isNull();

    for (int i = 0; i < 4; i++) {
      post("/api/v2/spans", SpanBytesEncoder.JSON_V2.encodeList(List.of(
        Span.newBuilder().traceId("a").id(i + 1).timestamp(TODAY).name("whopper")
          .putTag("clnt/finagle.version", "6.45." + i).build()
      )));
    }

    assertThat(get("/api/v2/autocompleteValues?key=environment").header("Cache-Control"))
      .isNull();
    assertThat(get("/api/v2/autocompleteValues?key=clnt/finagle.version").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .build()).execute();
  }

  private Response post(String path, byte[] body) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .post(RequestBody.create(body))
      .build()).execute();
  }
}
