package zipkin.server.internal;
/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.prometheus.client.Histogram;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.Codec;
import zipkin.Span;
import zipkin.server.ZipkinServer;
import zipkin2.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.LOTS_OF_SPANS;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "spring.config.name=zipkin-server"
)
@RunWith(SpringRunner.class)
public class ITZipkinMetricsHealth {

  @Autowired InMemoryStorage storage;
  @Autowired ActuateCollectorMetrics metrics;
  @Autowired Histogram duration;
  @Value("${local.server.port}") int zipkinPort;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  static Double readDouble(String json, String jsonPath) {
    return JsonPath.compile(jsonPath).read(json);
  }

  static String readString(String json, String jsonPath) {
    return JsonPath.compile(jsonPath).read(json);
  }

  static Integer readInteger(String json, String jsonPath) {
    return JsonPath.compile(jsonPath).read(json);
  }

  static List readJson(String json) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode jsonNode = mapper.readTree(json);
    List<String> fieldsList = new ArrayList<>();
    jsonNode.fieldNames().forEachRemaining(fieldsList::add);
    return fieldsList;
  }

  @Before public void init() {
    storage.clear();
    duration.clear();
    metrics.forTransport("http").clear();
  }

  @Test public void healthIsOK() throws Exception {
    assertThat(get("/health").isSuccessful())
      .isTrue();
  }

  @Test public void metricsIsOK() throws Exception {
    assertThat(get("/metrics").isSuccessful())
      .isTrue();
  }

  /** Makes sure the prometheus filter doesn't count twice */
  @Test public void writeSpans_updatesPrometheusMetrics() throws Exception {
    List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    byte[] body = Codec.JSON.writeSpans(spans);
    post("/api/v1/spans", body);
    post("/api/v1/spans", body);

    Response response = get("/prometheus");
    assertThat(response.isSuccessful()).isTrue();
    String prometheus = response.body().string();

    assertThat(prometheus)
      .contains("http_request_duration_seconds_count{path=\"/api/v1/spans\",method=\"POST\",} 2.0");
  }

  @Test public void writeSpans_updatesMetrics() throws Exception {
    List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    byte[] body = Codec.JSON.writeSpans(spans);
    post("/api/v1/spans", body);
    post("/api/v1/spans", body);

    Response response = get("/metrics");
    assertThat(response.isSuccessful()).isTrue();
    String json = response.body().string();

    assertThat(readInteger(json, "$.['counter.zipkin_collector.messages.http']"))
      .isEqualTo(2);
    assertThat(readInteger(json, "$.['counter.zipkin_collector.bytes.http']"))
      .isEqualTo(body.length * 2);
    assertThat(readDouble(json, "$.['gauge.zipkin_collector.message_bytes.http']"))
      .isEqualTo(body.length);
    assertThat(readInteger(json, "$.['counter.zipkin_collector.spans.http']"))
      .isEqualTo(spans.size() * 2);
    assertThat(readDouble(json, "$.['gauge.zipkin_collector.message_spans.http']"))
      .isEqualTo(spans.size());
  }

  @Test public void writeSpans_malformedUpdatesMetrics() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};
    post("/api/v1/spans", body);

    Response response = get("/metrics");
    assertThat(response.isSuccessful()).isTrue();
    String json = response.body().string();

    assertThat(readInteger(json, "$.['counter.zipkin_collector.messages.http']"))
      .isEqualTo(1);
    assertThat(readInteger(json, "$.['counter.zipkin_collector.messages_dropped.http']"))
      .isEqualTo(1);
  }

  @Test public void readsHealth() throws Exception {
    Response response = get("/health");
    assertThat(response.isSuccessful()).isTrue();
    String json = response.body().string();
    assertThat(readString(json, "$.status"))
      .isIn("UP", "DOWN", "UNKNOWN");
    assertThat(readString(json, "$.zipkin.status"))
      .isIn("UP", "DOWN", "UNKNOWN");
  }

  @Test public void writesSpans_readMetricsFormat() throws Exception {
    byte[] span = {'z', 'i', 'p', 'k', 'i', 'n'};
    List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1], LOTS_OF_SPANS[2]);
    byte[] body = Codec.JSON.writeSpans(spans);
    post("/api/v1/spans", body);
    post("/api/v1/spans", body);
    post("/api/v1/spans", span);
    Thread.sleep(1500);
    Response response = get("/metrics");
    assertThat(response.isSuccessful()).isTrue();
    String json = response.body().string();
    assertThat(readJson(json))
      .contains(
        "gauge.zipkin_collector.message_spans.http"
        , "gauge.zipkin_collector.message_bytes.http"
        , "counter.zipkin_collector.messages.http"
        , "counter.zipkin_collector.bytes.http"
        , "counter.zipkin_collector.spans.http"
        , "counter.zipkin_collector.messages_dropped.http"
      );
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + path)
      .build()).execute();
  }

  private Response post(String path, byte[] body) throws IOException {
    return client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + path)
      .post(RequestBody.create(null, body))
      .build()).execute();
  }
}
