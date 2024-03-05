/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Okio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.UTF_8;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
  }
)
public class ITZipkinServer {
  static final List<Span> TRACE = List.of(TestObjects.CLIENT_SPAN);

  @Autowired InMemoryStorage storage;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @BeforeEach void init() {
    storage.clear();
  }

  @Test void getTrace() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/trace/" + TRACE.get(0).traceId());
    assertThat(response.isSuccessful()).isTrue();

    assertThat(response.body().bytes())
      .containsExactly(SpanBytesEncoder.JSON_V2.encodeList(TRACE));
  }

  @Test void getTrace_notFound() throws Exception {
    Response response = get("/api/v2/trace/" + TRACE.get(0).traceId());
    assertThat(response.code()).isEqualTo(404);

    assertThat(response.body().string())
      .isEqualTo(TRACE.get(0).traceId() + " not found");
  }

  @Test void getTrace_malformed() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/trace/0e8b46e1-81b");
    assertThat(response.code()).isEqualTo(400);

    assertThat(response.body().string())
      .isEqualTo("0e8b46e1-81b should be lower-hex encoded with no prefix");
  }

  @Test void getTraces() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/traceMany?traceIds=abcd," + TRACE.get(0).traceId());
    assertThat(response.isSuccessful()).isTrue();

    assertThat(response.body().string())
      .isEqualTo("[" + new String(SpanBytesEncoder.JSON_V2.encodeList(TRACE), UTF_8) + "]");
  }

  @Test void getTraces_emptyNotOk() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/traceMany?traceIds=");
    assertThat(response.code()).isEqualTo(400);

    assertThat(response.body().string())
      .isEqualTo("traceIds parameter is empty");
  }

  @Test void getTraces_singleNotOk() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/traceMany?traceIds=" + TRACE.get(0).traceId());
    assertThat(response.code()).isEqualTo(400);

    assertThat(response.body().string())
      .isEqualTo("Use /api/v2/trace/{traceId} endpoint to retrieve a single trace");
  }

  @Test void getTraces_malformed() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/traceMany?traceIds=abcd,0e8b46e1-81b");
    assertThat(response.code()).isEqualTo(400);

    assertThat(response.body().string())
      .isEqualTo("0e8b46e1-81b should be lower-hex encoded with no prefix");
  }

  @Test void tracesQueryRequiresNoParameters() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/traces");
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body().string())
      .isEqualTo("[" + new String(SpanBytesEncoder.JSON_V2.encodeList(TRACE), UTF_8) + "]");
  }

  @Test void v2WiresUp() throws Exception {
    assertThat(get("/api/v2/services").isSuccessful())
      .isTrue();
  }

  @Test void doesntSetCacheControlOnNameEndpointsWhenLessThan4Services() throws Exception {
    storage.accept(TRACE).execute();

    assertThat(get("/api/v2/services").header("Cache-Control"))
      .isNull();

    assertThat(get("/api/v2/spans?serviceName=web").header("Cache-Control"))
      .isNull();

    assertThat(get("/api/v2/remoteServices?serviceName=web").header("Cache-Control"))
      .isNull();
  }

  @Test void spanNameQueryWorksWithNonAsciiServiceName() throws Exception {
    assertThat(get("/api/v2/spans?serviceName=个人信息服务").code())
      .isEqualTo(200);
  }

  @Test void remoteServiceNameQueryWorksWithNonAsciiServiceName() throws Exception {
    assertThat(get("/api/v2/remoteServices?serviceName=个人信息服务").code())
      .isEqualTo(200);
  }

  @Test void remoteServiceNameReturnsCorrectJsonForEscapedWhitespaceInName()
    throws Exception {
    storage.accept(List.of(CLIENT_SPAN.toBuilder()
      .localEndpoint(FRONTEND.toBuilder().serviceName("foo\tbar").build())
      .build()))
      .execute();
    Response response = get("/api/v2/services");
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body().string()).isEqualTo("[\"foo\\tbar\"]");
  }

  @Test void setsCacheControlOnNameEndpointsWhenMoreThan3Services() throws Exception {
    List<String> services = List.of("foo", "bar", "baz", "quz");
    for (int i = 0; i < services.size(); i++) {
      storage.accept(List.of(
        Span.newBuilder().traceId("a").id(i + 1).timestamp(TODAY).name("whopper")
          .localEndpoint(Endpoint.newBuilder().serviceName(services.get(i)).build())
          .remoteEndpoint(Endpoint.newBuilder().serviceName(services.get(i) + 1).build())
          .build()
      )).execute();
    }

    assertThat(get("/api/v2/services").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");

    assertThat(get("/api/v2/spans?serviceName=web").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");

    assertThat(get("/api/v2/remoteServices?serviceName=web").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");

    // Check that the response is alphabetically sorted.
    assertThat(get("/api/v2/services").body().string())
      .isEqualTo("[\"bar\",\"baz\",\"foo\",\"quz\"]");
  }

  @Test void shouldAllowAnyOriginByDefault() throws Exception {
    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/traces"))
      .header("Origin", "http://foo.example.com")
      .build()).execute();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.header("vary")).isNull();
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-origin")).contains("*");
  }

  @Test void forwardsApiForUi() throws Exception {
    assertThat(get("/zipkin/api/v2/traces").isSuccessful()).isTrue();
    assertThat(get("/zipkin/api/v2/traces").isSuccessful()).isTrue();
  }

  /** Simulate a proxy which forwards / to zipkin as opposed to resolving / -> /zipkin first */
  @Test void redirectedHeaderUsesOriginalHostAndPort() throws Exception {
    Request forwarded = new Request.Builder()
      .url(url(server, "/"))
      .addHeader("Host", "zipkin.com")
      .addHeader("X-Forwarded-Proto", "https")
      .addHeader("X-Forwarded-Port", "444")
      .build();

    Response response = client.newBuilder().followRedirects(false).build()
      .newCall(forwarded).execute();

    // Redirect header should be the proxy, not the backed IP/port
    assertThat(response.header("Location"))
      .isEqualTo("/zipkin/");
  }

  @Test void infoEndpointIsAvailable() throws IOException {
    Response info = get("/info");
    assertThat(info.isSuccessful()).isTrue();
    assertThat(info.body().contentType().toString())
      .isEqualTo("application/json; charset=utf-8");
    assertThat(info.body().string())
      .isEqualToIgnoringWhitespace(stringFromClasspath(getClass(), "info.json"));
  }

  @Test void getTrace_spaceAfterTraceId() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/trace/" + TRACE.get(0).traceId() + " ");
    assertThat(response.isSuccessful()).isTrue();

    assertThat(response.body().bytes())
      .containsExactly(SpanBytesEncoder.JSON_V2.encodeList(TRACE));
  }

  @Test void traceMethodDisallowed() {
    // trace method is disallowed for any route but we just test couple of paths here, can't test them all
    Arrays.stream(new String[]{"/", "/api/v2/traces", "/whatever/and/not"}).forEach(path -> {
      final Response response;
      try {
        response = client.newCall(new Request.Builder().url(url(server, path))
          .method("TRACE", null).build())
          .execute();
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.code()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.code());
      } catch (IOException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
    });
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .build()).execute();
  }

  public static String url(Server server, String path) {
    return "http://localhost:" + server.activeLocalPort() + path;
  }

  public static String stringFromClasspath(Class<?> thisClass, String path) throws IOException {
    URL url = thisClass.getClassLoader().getResource(path);
    assertThat(url).isNotNull();

    try (InputStream fromClasspath = url.openStream()) {
      return Okio.buffer(Okio.source(fromClasspath)).readUtf8();
    }
  }
}
