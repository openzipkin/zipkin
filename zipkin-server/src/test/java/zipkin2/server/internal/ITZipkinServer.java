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
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.util.Arrays;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.UTF_8;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {"spring.config.name=zipkin-server", "armeria.port=0"}
)
@RunWith(SpringRunner.class)
public class ITZipkinServer {
  static final List<Span> TRACE = Arrays.asList(TestObjects.CLIENT_SPAN);

  @Autowired InMemoryStorage storage;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @Before public void init() {
    storage.clear();
  }

  @Test public void writeSpans_noContentTypeIsJson() throws Exception {
    Response response = post("/api/v2/spans", SpanBytesEncoder.JSON_V2.encodeList(TRACE));

    assertThat(response.code())
      .isEqualTo(202);
  }

  @Test public void writeSpans_version2() throws Exception {
    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(TRACE);

    assertThat(post("/api/v2/spans", message).code())
      .isEqualTo(202);

    // sleep as the the storage operation is async
    Thread.sleep(1500);

    Response response = get("/api/v2/trace/" + TRACE.get(0).traceId());
    assertThat(response.isSuccessful()).isTrue();

    assertThat(response.body().bytes())
      .containsExactly(message);
  }

  @Test public void tracesQueryRequiresNoParameters() throws Exception {
    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(TRACE);
    post("/api/v2/spans", message);

    Response response = get("/api/v2/traces");
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body().string())
      .isEqualTo("[" + new String(message, UTF_8) + "]");
  }

  @Test public void writeSpans_malformedJsonIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};

    Response response = post("/api/v2/spans", body);
    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string())
      .startsWith("Malformed reading List<Span> from json");
  }

  @Test public void writeSpans_malformedGzipIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};

    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .header("Content-Encoding", "gzip") // << gzip here, but the body isn't!
      .post(RequestBody.create(null, body))
      .build()).execute();

    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string())
      .startsWith("Cannot gunzip spans");
  }

  @Test public void writeSpans_contentTypeXThrift() throws Exception {
    byte[] message = SpanBytesEncoder.THRIFT.encodeList(TRACE);

    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/v1/spans"))
      .post(RequestBody.create(MediaType.parse("application/x-thrift"), message))
      .build()).execute();

    assertThat(response.code())
      .withFailMessage(response.body().string())
      .isEqualTo(202);
  }

  @Test public void writeSpans_malformedThriftIsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};

    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/v1/spans"))
      .post(RequestBody.create(MediaType.parse("application/x-thrift"), body))
      .build()).execute();

    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string())
      .endsWith("reading List<Span> from TBinary");
  }

  @Test public void writeSpans_contentTypeXProtobuf() throws Exception {
    byte[] message = SpanBytesEncoder.PROTO3.encodeList(TRACE);

    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .post(RequestBody.create(MediaType.parse("application/x-protobuf"), message))
      .build()).execute();

    assertThat(response.code())
      .isEqualTo(202);
  }

  @Test public void writeSpans_malformedProto3IsBadRequest() throws Exception {
    byte[] body = {'h', 'e', 'l', 'l', 'o'};

    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .post(RequestBody.create(MediaType.parse("application/x-protobuf"), body))
      .build()).execute();

    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string())
      .startsWith("Truncated: length 101 > bytes remaining 3 reading List<Span> from proto3");
  }

  @Test public void v2WiresUp() throws Exception {
    assertThat(get("/api/v2/services").isSuccessful())
      .isTrue();
  }

  @Test public void writeSpans_gzipEncoded() throws Exception {
    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(TRACE);

    Buffer sink = new Buffer();
    GzipSink gzipSink = new GzipSink(sink);
    gzipSink.write(new Buffer().write(message), message.length);
    gzipSink.close();
    byte[] gzippedBody = sink.readByteArray();

    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .header("Content-Encoding", "gzip")
      .post(RequestBody.create(null, gzippedBody))
      .build()).execute();

    assertThat(response.isSuccessful());
  }

  @Test public void doesntSetCacheControlOnNameEndpointsWhenLessThan4Services() throws Exception {
    byte[] message = SpanBytesEncoder.JSON_V2.encodeList(TRACE);
    post("/api/v2/spans", message);

    assertThat(get("/api/v2/services").header("Cache-Control"))
      .isNull();

    assertThat(get("/api/v2/spans?serviceName=web").header("Cache-Control"))
      .isNull();
  }

  @Test public void setsCacheControlOnNameEndpointsWhenMoreThan3Services() throws Exception {
    List<String> services = Arrays.asList("foo", "bar", "baz", "quz");
    for (int i = 0; i < services.size(); i++) {
      post("/api/v2/spans", SpanBytesEncoder.JSON_V2.encodeList(Arrays.asList(
        Span.newBuilder().traceId("a").id(i + 1).timestamp(TODAY).name("whopper").localEndpoint(
          Endpoint.newBuilder().serviceName(services.get(i)).build()
        ).build()
      )));
    }

    assertThat(get("/api/v2/services").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");

    assertThat(get("/api/v2/spans?serviceName=web").header("Cache-Control"))
      .isEqualTo("max-age=300, must-revalidate");
  }

  @Test public void shouldAllowAnyOriginByDefault() throws Exception {
    Response response = client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/traces"))
      .header("Origin", "http://foo.example.com")
      .build()).execute();

    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.header("vary")).isNull();
    assertThat(response.header("access-control-allow-credentials")).isNull();
    assertThat(response.header("access-control-allow-origin")).contains("*");
  }

  @Test public void forwardsApiForUi() throws Exception {
    assertThat(get("/zipkin/api/v2/traces").isSuccessful()).isTrue();
    assertThat(get("/zipkin/api/v2/traces").isSuccessful()).isTrue();
  }

  /** Simulate a proxy which forwards / to zipkin as opposed to resolving / -> /zipkin first */
  @Test public void redirectedHeaderUsesOriginalHostAndPort() throws Exception {
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
      .isEqualTo("./zipkin/");
  }

  @Test public void infoEndpointIsAvailable() throws IOException {
    assertThat(get("/info").isSuccessful()).isTrue();
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .build()).execute();
  }

  private Response post(String path, byte[] body) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .post(RequestBody.create(null, body))
      .build()).execute();
  }

  public static String url(Server server, String path) {
    return "http://localhost:" + server.activePort().get().localAddress().getPort() + path;
  }
}
