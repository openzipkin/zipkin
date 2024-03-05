/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.brave;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;
import zipkin2.Component;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static zipkin2.TestObjects.DAY;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.server.internal.ITZipkinServer.url;

/**
 * This class is flaky for as yet unknown reasons. For example, in CI, sometimes assertions fail
 * due to incomplete traces. Hence, it includes more assertion customization than normal.
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    "zipkin.self-tracing.enabled=true",
    "zipkin.self-tracing.message-timeout=100ms",
    "zipkin.self-tracing.traces-per-second=100"
  })
class ITZipkinSelfTracing {
  @Autowired TracingStorageComponent storage;
  @Autowired AsyncZipkinSpanHandler zipkinSpanHandler;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @BeforeEach void clear() {
    inMemoryStorage().clear();
  }

  InMemoryStorage inMemoryStorage() {
    return (InMemoryStorage) storage.delegate;
  }

  @Test void getIsTraced_v2() throws Exception {
    assertThat(getServices("v2").body().string()).isEqualTo("[]");

    List<List<Span>> traces = awaitSpans(2);

    assertQueryReturnsResults(QueryRequest.newBuilder()
      .annotationQuery(Map.of("http.path", "/api/v2/services")), traces);

    assertQueryReturnsResults(QueryRequest.newBuilder().spanName("get-service-names"), traces);
  }

  @Test
  @Disabled("https://github.com/openzipkin/zipkin/issues/2781")
  void postIsTraced_v1() throws Exception {
    postSpan("v1");

    List<List<Span>> traces = awaitSpans(3); // test span + POST + accept-spans

    assertQueryReturnsResults(QueryRequest.newBuilder()
      .annotationQuery(Map.of("http.path", "/api/v1/spans")), traces);

    assertQueryReturnsResults(QueryRequest.newBuilder().spanName("accept-spans"), traces);
  }

  @Test
  @Disabled("https://github.com/openzipkin/zipkin/issues/2781")
  void postIsTraced_v2() throws Exception {
    postSpan("v2");

    List<List<Span>> traces = awaitSpans(3); // test span + POST + accept-spans

    assertQueryReturnsResults(QueryRequest.newBuilder()
      .annotationQuery(Map.of("http.path", "/api/v2/spans")), traces);

    assertQueryReturnsResults(QueryRequest.newBuilder().spanName("accept-spans"), traces);
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() {
    assertThat(storage).hasToString("Traced{InMemoryStorage{}}");
    assertThat(zipkinSpanHandler).hasToString("AsyncReporter{StorageComponent}");
  }

  List<List<Span>> awaitSpans(int count) {
    await().untilAsserted(() -> { // wait for spans
      List<List<Span>> traces = inMemoryStorage().getTraces();
      long received = traces.stream().mapToLong(List::size).sum();
      assertThat(inMemoryStorage().acceptedSpanCount())
        .withFailMessage("Wanted %s spans: got %s. Current traces: %s", count, received, traces)
        .isGreaterThanOrEqualTo(count);
    });
    return inMemoryStorage().getTraces();
  }

  void assertQueryReturnsResults(QueryRequest.Builder builder, List<List<Span>> traces)
    throws IOException {
    QueryRequest query = builder.endTs(System.currentTimeMillis()).lookback(DAY).limit(2).build();
    assertThat(inMemoryStorage().getTraces(query).execute())
      .withFailMessage("Expected results from %s. Current traces: %s", query, traces)
      .isNotEmpty();
  }

  /**
   * This POSTs a single span. Afterwards, we expect this trace in storage, and also the self-trace
   * of POSTing it.
   */
  void postSpan(String version) throws IOException {
    SpanBytesEncoder encoder =
      "v1".equals(version) ? SpanBytesEncoder.JSON_V1 : SpanBytesEncoder.JSON_V2;

    List<Span> testTrace = List.of(
      Span.newBuilder().timestamp(TODAY).traceId("1").id("2").name("test-trace").build()
    );

    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/" + version + "/spans"))
      .post(RequestBody.create(encoder.encodeList(testTrace)))
      .build())
      .execute();
    assertSuccessful(response);
  }

  Response getServices(String version) throws IOException {
    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/" + version + "/services"))
      .build())
      .execute();
    assertSuccessful(response);
    return response;
  }

  static void assertSuccessful(Response response) throws IOException {
    assertThat(response.isSuccessful())
      .withFailMessage("unsuccessful %s: %s", response.request(),
        response.peekBody(Long.MAX_VALUE).string())
      .isTrue();
  }
}
