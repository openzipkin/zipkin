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
package zipkin2.server.internal.prometheus;

import com.jayway.jsonpath.JsonPath;
import com.linecorp.armeria.server.Server;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import zipkin.server.ZipkinServer;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.server.internal.ITZipkinServer.url;

/**
 * Tests here look at values based on counter values, so need to run independently. It would seem
 * correct to {@link PrometheusMeterRegistry#clear() clear the registry} to isolate counters
 * incremented in one test from interfering with another. However, this clears the metrics
 * themselves, resulting in an empty {@link PrometheusMeterRegistry#scrape()}.
 *
 * <p>Currently, the only way we know how to reset the whole registry is to recreate the Spring
 * context, via {@link DirtiesContext} on each test. This is extremely slow, so please only add
 * tests that require isolation here!
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server"
  }
)
// Clearing the prometheus registry also clears the metrics themselves, not just the values, so we
// have to use dirties context so that each test runs in a separate instance of Spring Boot.
@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)
public class ITZipkinMetricsDirty {

  @Autowired InMemoryStorage storage;
  @Autowired PrometheusMeterRegistry registry;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @BeforeEach void init() {
    // We use DirtiesContext, not registry.clear(), as the latter would cause an empty scrape
    storage.clear();
  }

  @Test void writeSpans_updatesMetrics() throws Exception {
    List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    byte[] body = SpanBytesEncoder.JSON_V2.encodeList(spans);
    double messagesCount =
      registry.counter("zipkin_collector.messages", "transport", "http").count();
    double bytesCount = registry.counter("zipkin_collector.bytes", "transport", "http").count();
    double spansCount = registry.counter("zipkin_collector.spans", "transport", "http").count();
    post("/api/v2/spans", body);
    post("/api/v2/spans", body);

    String json = getAsString("/metrics");

    assertThat(readDouble(json, "$.['counter.zipkin_collector.messages.http']"))
      .isEqualTo(messagesCount + 2.0);
    assertThat(readDouble(json, "$.['counter.zipkin_collector.bytes.http']"))
      .isEqualTo(bytesCount + (body.length * 2));
    assertThat(readDouble(json, "$.['gauge.zipkin_collector.message_bytes.http']"))
      .isEqualTo(body.length);
    assertThat(readDouble(json, "$.['counter.zipkin_collector.spans.http']"))
      .isEqualTo(spansCount + (spans.size() * 2));
    assertThat(readDouble(json, "$.['gauge.zipkin_collector.message_spans.http']"))
      .isEqualTo(spans.size());
  }

  @Test void writeSpans_malformedUpdatesMetrics() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};
    double messagesCount =
      registry.counter("zipkin_collector.messages", "transport", "http").count();
    double messagesDroppedCount =
      registry.counter("zipkin_collector.messages_dropped", "transport", "http").count();
    post("/api/v2/spans", body);

    String json = getAsString("/metrics");

    assertThat(readDouble(json, "$.['counter.zipkin_collector.messages.http']"))
      .isEqualTo(messagesCount + 1);
    assertThat(readDouble(json, "$.['counter.zipkin_collector.messages_dropped.http']"))
      .isEqualTo(messagesDroppedCount + 1);
  }

  /** This tests logic in {@code BodyIsExceptionMessage} is scoped to POST requests. */
  @Test void getTrace_malformedDoesntUpdateCollectorMetrics() throws Exception {
    double messagesCount =
      registry.counter("zipkin_collector.messages", "transport", "http").count();
    double messagesDroppedCount =
      registry.counter("zipkin_collector.messages_dropped", "transport", "http").count();

    Response response = get("/api/v2/trace/0e8b46e1-81b");
    assertThat(response.code()).isEqualTo(400);

    String json = getAsString("/metrics");

    assertThat(readDouble(json, "$.['counter.zipkin_collector.messages.http']"))
      .isEqualTo(messagesCount);
    assertThat(readDouble(json, "$.['counter.zipkin_collector.messages_dropped.http']"))
      .isEqualTo(messagesDroppedCount);
  }

  /**
   * Makes sure the prometheus filter doesn't count twice
   */
  @Test void writeSpans_updatesPrometheusMetrics() throws Exception {
    List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    byte[] body = SpanBytesEncoder.JSON_V2.encodeList(spans);

    post("/api/v2/spans", body);
    post("/api/v2/spans", body);

    Thread.sleep(100); // sometimes CI flakes getting the "http.server.requests" timer
    double messagesCount = registry.counter("zipkin_collector.spans", "transport", "http").count();
    // Get the http count from the registry and it should match the summation previous count
    // and count of calls below
    long httpCount = registry
      .find("http.server.requests")
      .tag("uri", "/api/v2/spans")
      .timer()
      .count();

    // ensure unscoped counter does not exist
    assertThat(scrape())
      .doesNotContain("zipkin_collector_spans_total " + messagesCount)
      .contains("zipkin_collector_spans_total{transport=\"http\",} " + messagesCount)
      .contains(
        "http_server_requests_seconds_count{method=\"POST\",status=\"202\",uri=\"/api/v2/spans\",} "
          + httpCount);
  }

  String getAsString(String path) throws IOException {
    Response response = get(path);
    assertThat(response.isSuccessful())
      .withFailMessage(response.toString())
      .isTrue();
    return response.body().string();
  }

  Response get(String path) throws IOException {
    return client.newCall(new Request.Builder().url(url(server, path)).build()).execute();
  }

  Response post(String path, byte[] body) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .post(RequestBody.create(body))
      .build()).execute();
  }

  String scrape() throws Exception {
    Thread.sleep(100);
    return registry.scrape();
  }

  static double readDouble(String json, String jsonPath) {
    return JsonPath.compile(jsonPath).read(json);
  }
}
