/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.UTF_8;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "spring.config.name=zipkin-server"
)
@RunWith(SpringRunner.class)
public class ITZipkinServer {
  static final List<Span> TRACE = asList(TestObjects.CLIENT_SPAN);

  @Autowired InMemoryStorage storage;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).build();

  @Before public void init() {
    storage.clear();
  }

  @Test public void getTrace() throws Exception {
    storage.accept(TRACE).execute();

    Response response = get("/api/v2/trace/" + TRACE.get(0).traceId());
    assertThat(response.isSuccessful()).isTrue();

    assertThat(response.body().bytes())
      .containsExactly(SpanBytesEncoder.JSON_V2.encodeList(TRACE));
  }

  @Test public void tracesQueryRequiresNoParameters() throws Exception {
    storage.accept(TRACE).execute();
    
    Response response = get("/api/v2/traces");
    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.body().string())
      .isEqualTo("[" + new String(SpanBytesEncoder.JSON_V2.encodeList(TRACE), UTF_8) + "]");
  }

  @Test public void v2WiresUp() throws Exception {
    assertThat(get("/api/v2/services").isSuccessful())
      .isTrue();
  }

  @Test public void doesntSetCacheControlOnNameEndpointsWhenLessThan4Services() throws Exception {
    storage.accept(TRACE).execute();

    assertThat(get("/api/v2/services").header("Cache-Control"))
      .isNull();

    assertThat(get("/api/v2/spans?serviceName=web").header("Cache-Control"))
      .isNull();

    assertThat(get("/api/v2/remoteServices?serviceName=web").header("Cache-Control"))
      .isNull();
  }

  @Test public void spanNameQueryWorksWithNonAsciiServiceName() throws Exception {
    assertThat(get("/api/v2/spans?serviceName=个人信息服务").code())
      .isEqualTo(200);
  }

  @Test public void remoteServiceNameQueryWorksWithNonAsciiServiceName() throws Exception {
    assertThat(get("/api/v2/remoteServices?serviceName=个人信息服务").code())
      .isEqualTo(200);
  }

  @Test public void setsCacheControlOnNameEndpointsWhenMoreThan3Services() throws Exception {
    List<String> services = asList("foo", "bar", "baz", "quz");
    for (int i = 0; i < services.size(); i++) {
      storage.accept(asList(
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
      .isEqualTo("/zipkin/");
  }

  @Test public void infoEndpointIsAvailable() throws IOException {
    assertThat(get("/info").isSuccessful()).isTrue();
  }

  private Response get(String path) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, path))
      .build()).execute();
  }

  public static String url(Server server, String path) {
    return "http://localhost:" + server.activePort().get().localAddress().getPort() + path;
  }
}
