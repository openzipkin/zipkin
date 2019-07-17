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
package zipkin2.server.internal.brave;

import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.util.List;
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
import zipkin2.Component;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.QueryRequest;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static zipkin2.TestObjects.DAY;
import static zipkin2.server.internal.ITZipkinServer.url;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "spring.config.name=zipkin-server",
    "zipkin.self-tracing.enabled=true",
    "zipkin.self-tracing.message-timeout=1ms",
    "zipkin.self-tracing.traces-per-second=10"
  })
@RunWith(SpringRunner.class)
public class ITZipkinSelfTracing {
  @Autowired TracingStorageComponent storage;
  @Autowired AsyncReporter<Span> reporter;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Before public void clear() {
    inMemoryStorage().clear();
  }

  InMemoryStorage inMemoryStorage() {
    return (InMemoryStorage) storage.delegate;
  }

  @Test public void getIsTraced_v2() throws Exception {
    assertThat(get("v2").body().string()).isEqualTo("[]");

    awaitSpans();

    assertThat(getTraces(QueryRequest.newBuilder()
      .annotationQuery(singletonMap("http.path", "/api/v2/services")))).isNotEmpty();

    assertThat(getTraces(QueryRequest.newBuilder().spanName("get-service-names"))).isNotEmpty();
  }

  @Test public void postIsTraced_v1() throws Exception {
    post("v1");

    awaitSpans();

    assertThat(getTraces(QueryRequest.newBuilder()
      .annotationQuery(singletonMap("http.path", "/api/v1/spans")))).isNotEmpty();

    // TODO: add local span labeling the storage request
    //assertThat(getTraces(QueryRequest.newBuilder().spanName("accept-spans"))).isNotEmpty();
  }

  @Test public void postIsTraced_v2() throws Exception {
    post("v2");

    awaitSpans();

    assertThat(getTraces(QueryRequest.newBuilder()
      .annotationQuery(singletonMap("http.path", "/api/v2/spans")))).isNotEmpty();

    // TODO: add local span labeling the storage request
    //assertThat(getTraces(QueryRequest.newBuilder().spanName("accept-spans"))).isNotEmpty();
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test public void toStringContainsOnlySummaryInformation() {
    assertThat(storage).hasToString("Traced{InMemoryStorage{traceCount=0}}");
    assertThat(reporter).hasToString("AsyncReporter{StorageComponent}");
  }

  void awaitSpans() {
    await().untilAsserted(// wait for spans
      () -> assertThat(inMemoryStorage().acceptedSpanCount()).isGreaterThanOrEqualTo(1));
  }

  void post(String version) throws IOException {
    client.newCall(new Request.Builder()
      .url(url(server, "/api/" + version + "/spans"))
      .header("x-b3-sampled", "1") // we don't trace POST by default
      .post(RequestBody.create(null, "[" + "]"))
      .build())
      .execute();
  }

  List<List<Span>> getTraces(QueryRequest.Builder request) throws IOException {
    return inMemoryStorage().getTraces(
      request.endTs(System.currentTimeMillis()).lookback(DAY).limit(2).build()
    ).execute();
  }

  Response get(String version) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, "/api/" + version + "/services"))
      .build())
      .execute();
  }
}
