/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.server;

import com.jayway.jsonpath.JsonPath;
import io.prometheus.client.Histogram;
import java.io.IOException;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.GzipSink;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.Codec;
import zipkin.Span;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.V2SpanConverter;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.LOTS_OF_SPANS;
import static zipkin.TestObjects.TRACE;
import static zipkin.TestObjects.span;
import static zipkin.internal.Util.UTF_8;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "spring.config.name=zipkin-server"
)
@RunWith(SpringRunner.class)
public class ITZipkinServer {

  @Autowired InMemoryStorage storage;
  @Autowired ActuateCollectorMetrics metrics;
  @Autowired Histogram duration;
  @LocalServerPort int zipkinPort;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Before public void init() {
    storage.clear();
    duration.clear();
    metrics.forTransport("http").reset();
  }

  @Test public void writeSpans_noContentTypeIsJson() throws Exception {
    Response response = post("/api/v1/spans", Codec.JSON.writeSpans(TRACE));

    assertThat(response.code())
      .isEqualTo(202);
  }

  @Test public void writeSpans_version2() throws Exception {
    Span span = ApplyTimestampAndDuration.apply(LOTS_OF_SPANS[0]);

    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(asList(
      V2SpanConverter.fromSpan(span).get(0)
    ));

    assertThat(post("/api/v2/spans", message).code())
      .isEqualTo(202);

    // sleep as the the storage operation is async
    Thread.sleep(1500);

    Response response = get("/api/v1/trace/" + span.traceIdString());
    assertThat(response.isSuccessful()).isTrue();

    // We read it back in span v1 format
    assertThat(response.body().bytes())
      .isEqualTo(Codec.JSON.writeSpans(asList(span)));
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

  @Test public void tracesQueryRequiresNoParameters() throws Exception {
    byte[] body = Codec.JSON.writeSpans(TRACE);
    post("/api/v1/spans", body);

    Response response = get("/api/v1/traces");
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body().string())
      .isEqualTo("[" + new String(body, UTF_8) + "]");
  }

  @Test public void writeSpans_malformedJsonIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};

    Response response = post("/api/v1/spans", body);
    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string())
      .startsWith("Malformed reading List<Span> from json");
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

  @Test public void writeSpans_malformedGzipIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};

    Response response = client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/v1/spans")
      .header("Content-Encoding", "gzip") // << gzip here, but the body isn't!
      .post(RequestBody.create(null, body))
      .build()).execute();

    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string())
      .startsWith("Cannot gunzip spans");
  }

  @Test public void writeSpans_contentTypeXThrift() throws Exception {
    byte[] body = Codec.THRIFT.writeSpans(TRACE);

    Response response = client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/v1/spans")
      .post(RequestBody.create(MediaType.parse("application/x-thrift"), body))
      .build()).execute();

    assertThat(response.code())
      .isEqualTo(202);
  }

  @Test public void writeSpans_malformedThriftIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};

    Response response = client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/v1/spans")
      .post(RequestBody.create(MediaType.parse("application/x-thrift"), body))
      .build()).execute();

    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string())
      .startsWith("Malformed reading List<Span> from TBinary");
  }

  @Test public void healthIsOK() throws Exception {
    assertThat(get("/health").isSuccessful())
      .isTrue();
  }

  @Test public void v2WiresUp() throws Exception {
    assertThat(get("/api/v2/services").isSuccessful())
      .isTrue();
  }

  public void writeSpans_gzipEncoded() throws Exception {
    byte[] body = Codec.JSON.writeSpans(TRACE);

    Buffer sink = new Buffer();
    GzipSink gzipSink = new GzipSink(sink);
    gzipSink.write(new Buffer().write(body), body.length);
    gzipSink.close();
    byte[] gzippedBody = sink.readByteArray();

    Response response = client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/v1/spans")
      .header("Content-Encoding", "gzip")
      .post(RequestBody.create(null, gzippedBody))
      .build()).execute();

    assertThat(response.isSuccessful());
  }

  @Test public void readsRawTrace() throws Exception {
    Span span = TRACE.get(0);

    // write the span to the server, twice
    post("/api/v1/spans", Codec.JSON.writeSpans(asList(span)));
    post("/api/v1/spans", Codec.JSON.writeSpans(asList(span)));

    // sleep as the the storage operation is async
    Thread.sleep(1500);

    // Default will merge by span id
    Response response = get(format("/api/v1/trace/%016x", span.traceId));
    assertThat(response.isSuccessful());
    assertThat(response.body().bytes())
      .isEqualTo(Codec.JSON.writeSpans(asList(span)));

    // In the in-memory (or cassandra) stores, a raw read will show duplicate span rows.
    Response raw = get(format("/api/v1/trace/%016x?raw", span.traceId));
    assertThat(raw.isSuccessful());
    assertThat(raw.body().bytes())
      .isEqualTo(Codec.JSON.writeSpans(asList(span, span)));
  }

  @Test public void getBy128BitId() throws Exception {
    Span span1 = TRACE.get(0).toBuilder().traceIdHigh(1L).build();
    Span span2 = span1.toBuilder().traceIdHigh(2L).build();

    Response post = post("/api/v1/spans", Codec.JSON.writeSpans(asList(span1, span2)));
    assertThat(post.isSuccessful());

    // sleep as the the storage operation is async
    Thread.sleep(1500);

    // Tosses high bits
    Response response = get(format("/api/v1/trace/%016x%016x", span2.traceIdHigh, span2.traceId));

    assertThat(response.isSuccessful());
    assertThat(response.body().bytes())
      .isEqualTo(Codec.JSON.writeSpans(asList(span2)));
  }

  /** The zipkin-ui is a single-page app. This prevents reloading all resources on each click. */
  @Test public void setsMaxAgeOnUiResources() throws Exception {
    assertThat(get("/zipkin/favicon.ico").header("Cache-Control"))
      .isEqualTo("max-age=31536000");
    assertThat(get("/zipkin/config.json").header("Cache-Control"))
      .isEqualTo("max-age=600");
    assertThat(get("/zipkin/index.html").header("Cache-Control"))
      .isEqualTo("max-age=60");
  }

  @Test public void doesntSetCacheControlOnNameEndpointsWhenLessThan4Services() throws Exception {
    String json = "[" + new String(Codec.JSON.writeSpan(TRACE.get(0)), UTF_8) + "]";
    post("/api/v1/spans", json.getBytes(UTF_8));

    assertThat(get("/api/v1/services").header("Cache-Control"))
      .isNull();

    assertThat(get("/api/v1/spans?serviceName=web").header("Cache-Control"))
      .isNull();
  }

  @Test public void setsCacheControlOnNameEndpointsWhenMoreThan3Services() throws Exception {
    post("/api/v1/spans", Codec.JSON.writeSpans(TRACE));
    post("/api/v1/spans", Codec.JSON.writeSpans(asList(span(1))));

    assertThat(get("/api/v1/services").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");

    assertThat(get("/api/v1/spans?serviceName=web").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");
  }

  @Test public void shouldAllowAnyOriginByDefault() throws Exception {
    Response response = client.newCall(new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/api/v1/traces")
      .header("Origin", "http://foo.example.com")
      .build()).execute();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.header("vary")).contains("origin");
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-origin")).contains("*");
  }

  @Test public void forwardsApiForUi() throws Exception {
    assertThat(get("/zipkin/api/v1/traces").isSuccessful()).isTrue();
    assertThat(get("/zipkin/api/v2/traces").isSuccessful()).isTrue();
  }

  /** Simulate a proxy which forwards / to zipkin as opposed to resolving / -> /zipkin first */
  @Test public void redirectedHeaderUsesOriginalHostAndPort() throws Exception {
    Request forwarded = new Request.Builder()
      .url("http://localhost:" + zipkinPort + "/")
      .addHeader("Host", "zipkin.com")
      .addHeader("X-Forwarded-Proto", "https")
      .addHeader("X-Forwarded-Port", "444")
      .build();

    Response response = client.newCall(forwarded).execute();

    // Redirect header should be the proxy, not the backed IP/port
    assertThat(response.header("Location"))
      .isEqualTo("./zipkin/");
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

  static Integer readInteger(String json, String jsonPath) {
    return JsonPath.compile(jsonPath).read(json);
  }

  static Double readDouble(String json, String jsonPath) {
    return JsonPath.compile(jsonPath).read(json);
  }
}
