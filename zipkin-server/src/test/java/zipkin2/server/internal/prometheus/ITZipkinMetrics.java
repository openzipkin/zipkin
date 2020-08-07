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
package zipkin2.server.internal.prometheus;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.linecorp.armeria.server.Server;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.LOTS_OF_SPANS;
import static zipkin2.server.internal.ITZipkinServer.url;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server"
  }
)
@RunWith(SpringRunner.class)
public class ITZipkinMetrics {
  @Autowired InMemoryStorage storage;
  @Autowired PrometheusMeterRegistry registry;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @Before public void init() {
    storage.clear();
  }

  @Test public void metricsIsOK() throws Exception {
    assertThat(get("/metrics").isSuccessful())
      .isTrue();

    // ensure we don't track metrics in prometheus
    assertThat(scrape())
      .doesNotContain("metrics");
  }

  @Test public void prometheusIsOK() throws Exception {
    assertThat(get("/prometheus").isSuccessful())
      .isTrue();

    // ensure we don't track prometheus, UI requests in prometheus
    assertThat(scrape())
      .doesNotContain("prometheus")
      .doesNotContain("uri=\"/zipkin")
      .doesNotContain("uri=\"/\"");
  }

  @Test public void apiTemplate_prometheus() throws Exception {
    List<Span> spans = asList(LOTS_OF_SPANS[0]);
    byte[] body = SpanBytesEncoder.JSON_V2.encodeList(spans);
    assertThat(post("/api/v2/spans", body).isSuccessful())
      .isTrue();

    assertThat(get("/api/v2/trace/" + LOTS_OF_SPANS[0].traceId()).isSuccessful())
      .isTrue();

    assertThat(get("/api/v2/traceMany?traceIds=abcde," + LOTS_OF_SPANS[0].traceId()).isSuccessful())
      .isTrue();

    assertThat(scrape())
      .contains("uri=\"/api/v2/traceMany\"") // sanity check
      .contains("uri=\"/api/v2/trace/{traceId}\"")
      .doesNotContain(LOTS_OF_SPANS[0].traceId());
  }

  @Test public void forwardedRoute_prometheus() throws Exception {
    assertThat(get("/zipkin/api/v2/services").isSuccessful())
        .isTrue();

    assertThat(scrape())
        .contains("uri=\"/api/v2/services\"")
        .doesNotContain("uri=\"/zipkin/api/v2/services\"");
  }

  @Test public void jvmMetrics_prometheus() throws Exception {
    assertThat(scrape())
        .contains("jvm_memory_max_bytes")
        .contains("jvm_memory_used_bytes")
        .contains("jvm_memory_committed_bytes")
        .contains("jvm_buffer_count_buffers")
        .contains("jvm_buffer_memory_used_bytes")
        .contains("jvm_buffer_total_capacity_bytes")
        .contains("jvm_classes_loaded_classes")
        .contains("jvm_classes_unloaded_classes_total")
        .contains("jvm_threads_live_threads")
        .contains("jvm_threads_states_threads")
        .contains("jvm_threads_peak_threads")
        .contains("jvm_threads_daemon_threads");
    // gc metrics are not tested as are not present during test running
  }

  String scrape() throws InterruptedException {
    Thread.sleep(100);
    return registry.scrape();
  }

  /**
   * Makes sure the prometheus filter doesn't count twice
   */
  @Test public void writeSpans_updatesPrometheusMetrics() throws Exception {
    List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    byte[] body = SpanBytesEncoder.JSON_V2.encodeList(spans);

    post("/api/v2/spans", body);
    post("/api/v2/spans", body);

    Thread.sleep(100); // sometimes travis flakes getting the "http.server.requests" timer
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

  @Test public void writesSpans_readMetricsFormat() throws Exception {
    byte[] span = {'z', 'i', 'p', 'k', 'i', 'n'};
    List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    byte[] body = SpanBytesEncoder.JSON_V2.encodeList(spans);
    post("/api/v2/spans", body);
    post("/api/v2/spans", body);
    post("/api/v2/spans", span);
    Thread.sleep(1500);

    String metrics = getAsString("/metrics");

    assertThat(readJson(metrics)).containsOnlyKeys(
      "gauge.zipkin_collector.message_spans.http"
      , "gauge.zipkin_collector.message_bytes.http"
      , "counter.zipkin_collector.messages.http"
      , "counter.zipkin_collector.bytes.http"
      , "counter.zipkin_collector.spans.http"
      , "counter.zipkin_collector.messages_dropped.http"
      , "counter.zipkin_collector.spans_dropped.http"
    );
  }

  private String getAsString(String path) throws IOException {
    Response response = get(path);
    assertThat(response.isSuccessful())
      .withFailMessage(response.toString())
      .isTrue();
    return response.body().string();
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder().url(url(server, path)).build()).execute();
  }

  private Response post(String path, byte[] body) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .post(RequestBody.create(body))
      .build()).execute();
  }

  static Map<String, Integer> readJson(String json) throws Exception {
    Map<String, Integer> result = new LinkedHashMap<>();
    JsonParser parser = new JsonFactory().createParser(json);
    assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
    String nextField;
    while ((nextField = parser.nextFieldName()) != null) {
      result.put(nextField, parser.nextIntValue(0));
    }
    return result;
  }
}
