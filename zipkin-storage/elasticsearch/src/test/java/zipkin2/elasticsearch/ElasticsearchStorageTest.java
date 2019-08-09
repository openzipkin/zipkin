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

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.ResponseTimeoutException;
import com.linecorp.armeria.client.UnprocessedRequestException;
import com.linecorp.armeria.client.endpoint.EndpointGroupException;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import zipkin2.CheckResult;
import zipkin2.Component;
import zipkin2.elasticsearch.ElasticsearchStorage.LazyHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.DAY;

public class ElasticsearchStorageTest {

  static final BlockingQueue<AggregatedHttpRequest> CAPTURED_REQUESTS = new LinkedBlockingQueue<>();
  static final BlockingQueue<ServiceRequestContext> CAPTURED_CONTEXTS = new LinkedBlockingQueue<>();
  static final BlockingQueue<ServiceRequestContext> CAPTURED_HEALTH_CONTEXTS =
    new LinkedBlockingQueue<>();
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

  ElasticsearchStorage storage = ElasticsearchStorage.newBuilder(new LazyHttpClient() {
    @Override public HttpClient get() {
      return HttpClient.of(server.httpUri("/"));
    }

    @Override public String toString() {
      return server.httpUri("/");
    }
  }).build();

  @After public void tearDown() {
    storage.close();

    assertThat(MOCK_RESPONSES).isEmpty();

    // Tests don't have to take all requests.
    CAPTURED_REQUESTS.clear();
    CAPTURED_CONTEXTS.clear();
  }

  @Test public void memoizesIndexTemplate() throws Exception {
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

  static final AggregatedHttpResponse RESPONSE_UNAUTHORIZED = AggregatedHttpResponse.of(
    HttpStatus.UNAUTHORIZED,
    MediaType.JSON_UTF_8, // below is actual message from Amazon
    "{\"Message\":\"User: anonymous is not authorized to perform: es:ESHttpGet\"}}");

  @Test public void check() {
    MOCK_RESPONSES.add(HEALTH_RESPONSE);

    assertThat(storage.check()).isEqualTo(CheckResult.OK);
  }

  @Test public void check_unauthorized() {
    MOCK_RESPONSES.add(RESPONSE_UNAUTHORIZED);

    CheckResult result = storage.check();
    assertThat(result.ok()).isFalse();
    assertThat(result.error().getMessage())
      .isEqualTo("User: anonymous is not authorized to perform: es:ESHttpGet");
  }

  /**
   * See {@link HttpCallTest#unprocessedRequest()} which shows {@link UnprocessedRequestException}
   * are re-wrapped as {@link RejectedExecutionException}.
   */
  @Test public void isOverCapacity() {
    // timeout
    assertThat(storage.isOverCapacity(ResponseTimeoutException.get())).isTrue();

    // top-level
    assertThat(storage.isOverCapacity(new RejectedExecutionException(
      "{\"status\":429,\"error\":{\"type\":\"es_rejected_execution_exception\"}}"))).isTrue();

    // re-wrapped
    assertThat(storage.isOverCapacity(
      new RejectedExecutionException("Rejected execution: No endpoints.",
        new EndpointGroupException("No endpoints")))).isTrue();

    // not applicable
    assertThat(storage.isOverCapacity(new IllegalStateException("Rejected execution"))).isFalse();
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test public void toStringContainsOnlySummaryInformation() {
    assertThat(storage).hasToString(
      String.format("ElasticsearchStorage{initialEndpoints=%s, index=zipkin}",
        server.httpUri("/")));
  }
}
