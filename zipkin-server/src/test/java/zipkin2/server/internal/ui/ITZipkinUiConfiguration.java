/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.server.internal.ui;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
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
    "zipkin.ui.base-path=/foozipkin",
    "server.compression.enabled=true",
    "server.compression.min-response-size=128"
  })
@RunWith(SpringRunner.class)
public class ITZipkinUiConfiguration {
  @Autowired Server server;
  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Test public void configJson() throws Exception {
    assertThat(get("/zipkin/config.json").body().string()).isEqualTo(""
      + "{\n"
      + "  \"environment\" : \"\",\n"
      + "  \"queryLimit\" : 10,\n"
      + "  \"defaultLookback\" : 900000,\n"
      + "  \"searchEnabled\" : true,\n"
      + "  \"logsUrl\" : null,\n"
      + "  \"supportUrl\" : null,\n"
      + "  \"archivePostUrl\" : null,\n"
      + "  \"archiveUrl\" : null,\n"
      + "  \"dependency\" : {\n"
      + "    \"enabled\" : true,\n"
      + "    \"lowErrorRate\" : 0.5,\n"
      + "    \"highErrorRate\" : 0.75\n"
      + "  }\n"
      + "}"
    );
  }

  /** The zipkin-lens is a single-page app. This prevents reloading all resources on each click. */
  @Test public void setsMaxAgeOnUiResources() throws Exception {
    assertThat(get("/zipkin/config.json").header("Cache-Control"))
      .isEqualTo("max-age=600");
    assertThat(get("/zipkin/index.html").header("Cache-Control"))
      .isEqualTo("max-age=60");
    assertThat(get("/zipkin/test.txt").header("Cache-Control"))
      .isEqualTo("max-age=31536000");
  }

  @Test public void redirectsIndex() throws Exception {
    String index = get("/zipkin/index.html").body().string();

    client = new OkHttpClient.Builder().followRedirects(true).build();

    Stream.of("/zipkin", "/").forEach(path -> {
      try {
        assertThat(get(path).body().string()).isEqualTo(index);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  /** Browsers honor conditional requests such as eTag. Let's make sure the server does */
  @Test public void conditionalRequests() {
    Stream.of("/zipkin/config.json", "/zipkin/index.html", "/zipkin/test.txt").forEach(path -> {
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
  @Test public void supportsCompression() {
    assertThat(getContentEncodingFromRequestThatAcceptsGzip("/zipkin/test.txt"))
      .isNull(); // too small to compress
    assertThat(getContentEncodingFromRequestThatAcceptsGzip("/zipkin/config.json"))
      .isEqualTo("gzip");
  }

  /**
   * The test sets the property {@code zipkin.ui.base-path=/foozipkin}, which should reflect in
   * index.html
   */
  @Test public void replacesBaseTag() throws Exception {
    assertThat(get("/zipkin/index.html").body().string())
      .isEqualToIgnoringWhitespace(stringFromClasspath(getClass(), "zipkin-lens/index.html")
        .replace("<base href=\"/\" />", "<base href=\"/foozipkin/\">"));
  }

  /** index.html is served separately. This tests other content is also loaded from the classpath. */
  @Test public void servesOtherContentFromClasspath() throws Exception {
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
