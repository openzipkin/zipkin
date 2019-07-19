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
package zipkin2.elasticsearch;

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin2.CheckResult;
import zipkin2.Component;
import zipkin2.elasticsearch.internal.BasicAuthInterceptor;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.DAY;

public class ElasticsearchStorageTest {

  static final BlockingQueue<AggregatedHttpRequest> CAPTURED_REQUESTS = new LinkedBlockingQueue<>();
  static final BlockingQueue<ServiceRequestContext> CAPTURED_CONTEXTS = new LinkedBlockingQueue<>();
  static final BlockingQueue<ServiceRequestContext> CAPTURED_HEALTH_CONTEXTS = new LinkedBlockingQueue<>();
  static final BlockingQueue<AggregatedHttpResponse> MOCK_RESPONSES = new LinkedBlockingQueue<>();
  static final AggregatedHttpResponse SUCCESS_RESPONSE =
    AggregatedHttpResponse.of(ResponseHeaders.of(HttpStatus.OK), HttpData.EMPTY_DATA);

  @ClassRule public static ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) throws Exception {
      sb.http(0);
      sb.https(0);
      sb.tlsSelfSigned();

      sb.service("/_cluster/health", (ctx, req) -> {
        CAPTURED_HEALTH_CONTEXTS.add(ctx);
        return HttpResponse.of(SUCCESS_RESPONSE);
      });

      sb.serviceUnder("/", (ctx, req) -> {
        CAPTURED_CONTEXTS.add(ctx);
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        req.aggregate().thenAccept(agg -> {
          CAPTURED_REQUESTS.add(agg);
          AggregatedHttpResponse response = MOCK_RESPONSES.remove();
          responseFuture.complete(HttpResponse.of(response));
        }).exceptionally(t -> {
          responseFuture.completeExceptionally(t);
          return null;
        });
        return HttpResponse.from(responseFuture);
      });
    }
  };

  @Before public void setUp() {
    storage =
      ElasticsearchStorage.newBuilder()
        // https://github.com/line/armeria/issues/1895
        .clientFactoryCustomizer(factory -> factory.useHttp2Preface(true))
        .hosts(asList(server.httpUri("/")))
        .build();
  }

  @After public void tearDown() {
    storage.close();

    assertThat(MOCK_RESPONSES).isEmpty();

    // Tests don't have to take all requests.
    CAPTURED_REQUESTS.clear();
    CAPTURED_CONTEXTS.clear();
  }

  ElasticsearchStorage storage;

  @Test
  public void memoizesIndexTemplate() throws Exception {
    MOCK_RESPONSES.add(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, "{\"version\":{\"number\":\"6.7.0\"}}"));
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // get span template
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // get dependency template
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // get tags template
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // dependencies request
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // dependencies request

    long endTs = storage.indexNameFormatter().parseDate("2016-10-02");
    storage.spanStore().getDependencies(endTs, DAY).execute();
    storage.spanStore().getDependencies(endTs, DAY).execute();

    CAPTURED_REQUESTS.take(); // get version
    CAPTURED_REQUESTS.take(); // get span template
    CAPTURED_REQUESTS.take(); // get dependency template
    CAPTURED_REQUESTS.take(); // get tags template

    assertThat(CAPTURED_REQUESTS.take().path())
        .startsWith("/zipkin*dependency-2016-10-01,zipkin*dependency-2016-10-02/_search");
    assertThat(CAPTURED_REQUESTS.take().path())
        .startsWith("/zipkin*dependency-2016-10-01,zipkin*dependency-2016-10-02/_search");
  }

  static final AggregatedHttpResponse HEALTH_RESPONSE = AggregatedHttpResponse.of(
    HttpStatus.OK,
    MediaType.JSON_UTF_8,
    "{\n"
      + "  \"cluster_name\": \"elasticsearch_zipkin\",\n"
      + "  \"status\": \"yellow\",\n"
      + "  \"timed_out\": false,\n"
      + "  \"number_of_nodes\": 1,\n"
      + "  \"number_of_data_nodes\": 1,\n"
      + "  \"active_primary_shards\": 5,\n"
      + "  \"active_shards\": 5,\n"
      + "  \"relocating_shards\": 0,\n"
      + "  \"initializing_shards\": 0,\n"
      + "  \"unassigned_shards\": 5,\n"
      + "  \"delayed_unassigned_shards\": 0,\n"
      + "  \"number_of_pending_tasks\": 0,\n"
      + "  \"number_of_in_flight_fetch\": 0,\n"
      + "  \"task_max_waiting_in_queue_millis\": 0,\n"
      + "  \"active_shards_percent_as_number\": 50\n"
      + "}");

  static final AggregatedHttpResponse HEALTH_RESPONSE_UNAUTHORIZED = AggregatedHttpResponse.of(
    HttpStatus.UNAUTHORIZED,
    MediaType.JSON_UTF_8,
    "{\"Message\":\"User: anonymous is not authorized to perform: es:ESHttpGet\"}}");

  @Test
  public void check() {
    MOCK_RESPONSES.add(HEALTH_RESPONSE);

    assertThat(storage.check()).isEqualTo(CheckResult.OK);
  }

  @Test
  public void check_unauthorized() {
    MOCK_RESPONSES.add(HEALTH_RESPONSE_UNAUTHORIZED);

    assertThat(storage.check().ok()).isFalse();
  }

  @Test
  public void check_oneHostDown() {
    storage.close();
    storage =
        ElasticsearchStorage.newBuilder()
          .clientFactoryCustomizer(factory ->
            factory
              // https://github.com/line/armeria/issues/1895
              .useHttp2Preface(true)
              .connectTimeoutMillis(100))
          .hosts(asList("http://1.2.3.4:" + server.httpPort(), server.httpUri("/")))
          .build();

    MOCK_RESPONSES.add(HEALTH_RESPONSE);

    assertThat(storage.check()).isEqualTo(CheckResult.OK);
  }

  @Test
  public void check_usesCustomizer() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder()
      // https://github.com/line/armeria/issues/1895
      .clientFactoryCustomizer(factory -> factory.useHttp2Preface(true))
      .clientCustomizer(client -> client.decorator(
        delegate -> BasicAuthInterceptor.create(delegate, "Aladdin", "OpenSesame")))
      .hosts(asList(server.httpUri("/")))
      .build();

    MOCK_RESPONSES.add(HEALTH_RESPONSE);

    assertThat(storage.check()).isEqualTo(CheckResult.OK);

    assertThat(CAPTURED_REQUESTS.take().headers().get("Authorization")).isNotNull();
  }

  @Test
  public void check_ssl() throws Exception {
    storage.close();
    storage = ElasticsearchStorage
      .newBuilder()
      .clientFactoryCustomizer(factory ->
        factory
          // https://github.com/line/armeria/issues/1895
          .useHttp2Preface(true)
          .sslContextCustomizer(
            ssl -> ssl.trustManager(InsecureTrustManagerFactory.INSTANCE)))
      // Need localhost, not IP, as single IPs don't use health check groups.
      .hosts(asList("https://localhost:" + server.httpsPort() + "/"))
      .build();

    MOCK_RESPONSES.add(HEALTH_RESPONSE);

    assertThat(storage.check()).isEqualTo(CheckResult.OK);

    assertThat(CAPTURED_CONTEXTS.take().sessionProtocol().isTls()).isTrue();
    // Ensure the EndpointGroup check is also SSL
    assertThat(CAPTURED_HEALTH_CONTEXTS.take().sessionProtocol().isTls()).isTrue();

    MOCK_RESPONSES.add(HEALTH_RESPONSE_UNAUTHORIZED);

    assertThat(storage.check().ok()).isFalse();
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test public void toStringContainsOnlySummaryInformation() {
    assertThat(storage).hasToString(String.format("ElasticsearchStorage{hosts=[%s], index=zipkin}",
      server.httpUri("/")));
  }
}
