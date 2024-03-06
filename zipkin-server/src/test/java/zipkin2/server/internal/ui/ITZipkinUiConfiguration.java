/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.ui;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.ITZipkinServer.stringFromClasspath;
import static zipkin2.server.internal.ITZipkinServer.url;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    "zipkin.ui.base-path=/admin/zipkin",
    "server.compression.enabled=true",
    "server.compression.min-response-size=128"
  })
class ITZipkinUiConfiguration {
  @Autowired Server server;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Test void configJson() throws Exception {
    assertThat(get("/zipkin/config.json").body().string()).isEqualTo("""
      {
        "environment" : "",
        "queryLimit" : 10,
        "defaultLookback" : 900000,
        "searchEnabled" : true,
        "logsUrl" : null,
        "supportUrl" : null,
        "archivePostUrl" : null,
        "archiveUrl" : null,
        "dependency" : {
          "enabled" : true,
          "lowErrorRate" : 0.5,
          "highErrorRate" : 0.75
        }
      }"""
    );
  }

  /** The zipkin-lens is a single-page app. This prevents reloading all resources on each click. */
  @Test void setsMaxAgeOnUiResources() throws Exception {
    assertThat(get("/zipkin/config.json").header("Cache-Control"))
      .isEqualTo("max-age=600");
    assertThat(get("/zipkin/index.html").header("Cache-Control"))
      .isEqualTo("max-age=60");
    assertThat(get("/zipkin/test.txt").header("Cache-Control"))
      .isEqualTo("max-age=31536000");
  }

  @Test void redirectsIndex() throws Exception {
    String index = get("/zipkin/index.html").body().string();

    client = new OkHttpClient.Builder().followRedirects(true).build();

    List.of("/zipkin", "/").forEach(path -> {
      try {
        assertThat(get(path).body().string()).isEqualTo(index);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  /** Browsers honor conditional requests such as eTag. Let's make sure the server does */
  @Test void conditionalRequests() {
    List.of("/zipkin/config.json", "/zipkin/index.html", "/zipkin/test.txt").forEach(path -> {
      try {
        String etag = get(path).header("etag");
        assertThat(conditionalGet(path, etag).code())
          .isEqualTo(304);
        assertThat(conditionalGet(path, "aargh").code())
          .isEqualTo(200);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  /** Some assets are pretty big. ensure they use compression. */
  @Test void supportsCompression() {
    assertThat(getContentEncodingFromRequestThatAcceptsGzip("/zipkin/test.txt"))
      .isNull(); // too small to compress
    assertThat(getContentEncodingFromRequestThatAcceptsGzip("/zipkin/config.json"))
      .isEqualTo("gzip");
  }

  /**
   * The test sets the property {@code zipkin.ui.base-path=/foozipkin}, which should reflect in
   * index.html
   */
  @Test void replacesBaseTag() throws Exception {
    assertThat(get("/zipkin/index.html").body().string()).isEqualTo("""
      <!-- simplified version of /zipkin-lens/index.html -->
      <html>
        <head>
          <base href="/admin/zipkin/">
          <link rel="icon" href="./favicon.ico">
          <script type="module" crossorigin="" src="./static/js/index.js"></script>
          <link rel="stylesheet" href="./static/css/index.css">
        </head>
        <body>zipkin-lens</body>
      </html>
      """
    );
  }

  /** index.html is served separately. This tests other content is also loaded from the classpath. */
  @Test void servesOtherContentFromClasspath() throws Exception {
    assertThat(get("/zipkin/test.txt").body().string())
      .isEqualToIgnoringWhitespace(stringFromClasspath(getClass(), "zipkin-lens/test.txt"));
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder().url(url(server, path)).build()).execute();
  }

  private Response conditionalGet(String path, String etag) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .header("If-None-Match", etag)
      .build()).execute();
  }

  private String getContentEncodingFromRequestThatAcceptsGzip(String path) {
    // We typically use OkHttp in our tests, but that automatically unzips..
    AggregatedHttpResponse response = WebClient.of(url(server, "/"))
      .execute(RequestHeaders.of(HttpMethod.GET, path, HttpHeaderNames.ACCEPT_ENCODING, "gzip"))
      .aggregate().join();

    return response.headers().get(HttpHeaderNames.CONTENT_ENCODING);
  }
}
