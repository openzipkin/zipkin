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
package zipkin2.elasticsearch;

import com.linecorp.armeria.client.ResponseTimeoutException;
import com.linecorp.armeria.client.UnprocessedRequestException;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.endpoint.EndpointGroupException;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.CheckResult;
import zipkin2.Component;
import zipkin2.elasticsearch.ElasticsearchStorage.LazyHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.DAY;

class ElasticsearchStorageTest {
  static final AggregatedHttpResponse SUCCESS_RESPONSE =
    AggregatedHttpResponse.of(ResponseHeaders.of(HttpStatus.OK), HttpData.empty());

  @RegisterExtension static MockWebServerExtension server = new MockWebServerExtension();

  ElasticsearchStorage storage;

  @BeforeEach void setUp() {
    storage = newBuilder().build();
  }

  @AfterEach void tearDown() {
    storage.close();
  }

  @Test void ensureIndexTemplates_false() throws Exception {
    storage.close();
    storage = newBuilder().ensureTemplates(false).build();

    server.enqueue(SUCCESS_RESPONSE); // dependencies request

    long endTs = Instant.parse("2016-10-02T00:00:00Z").toEpochMilli();
    storage.spanStore().getDependencies(endTs, DAY).execute();

    assertThat(server.takeRequest().request().path())
      .startsWith("/zipkin*dependency-2016-10-01,zipkin*dependency-2016-10-02/_search");
  }

  @Test void memoizesIndexTemplate() throws Exception {
    server.enqueue(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, "{\"version\":{\"number\":\"6.7.0\"}}"));
    server.enqueue(SUCCESS_RESPONSE); // get span template
    server.enqueue(SUCCESS_RESPONSE); // get dependency template
    server.enqueue(SUCCESS_RESPONSE); // get tags template
    server.enqueue(SUCCESS_RESPONSE); // dependencies request
    server.enqueue(SUCCESS_RESPONSE); // dependencies request

    long endTs = Instant.parse("2016-10-02T00:00:00Z").toEpochMilli();
    storage.spanStore().getDependencies(endTs, DAY).execute();
    storage.spanStore().getDependencies(endTs, DAY).execute();

    server.takeRequest(); // get version
    server.takeRequest(); // get span template
    server.takeRequest(); // get dependency template
    server.takeRequest(); // get tags template

    assertThat(server.takeRequest().request().path())
      .startsWith("/zipkin*dependency-2016-10-01,zipkin*dependency-2016-10-02/_search");
    assertThat(server.takeRequest().request().path())
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

  static final AggregatedHttpResponse RESPONSE_VERSION_6 = AggregatedHttpResponse.of(
    HttpStatus.OK, MediaType.JSON_UTF_8, "{\"version\":{\"number\":\"6.7.0\"}}");

  @Test void check_ensuresIndexTemplates_memozied() {
    server.enqueue(RESPONSE_VERSION_6);
    server.enqueue(SUCCESS_RESPONSE); // get span template
    server.enqueue(SUCCESS_RESPONSE); // get dependency template
    server.enqueue(SUCCESS_RESPONSE); // get tags template

    server.enqueue(HEALTH_RESPONSE);

    assertThat(storage.check()).isEqualTo(CheckResult.OK);

    // Later checks do not redo index template requests
    server.enqueue(HEALTH_RESPONSE);

    assertThat(storage.check()).isEqualTo(CheckResult.OK);
  }

  // makes sure we don't NPE
  @Test void check_ensuresIndexTemplates_fail_onNoContent() {
    server.enqueue(SUCCESS_RESPONSE); // empty instead of version json

    CheckResult result = storage.check();
    assertThat(result.ok()).isFalse();
    assertThat(result.error().getMessage())
      .isEqualTo("No content reading Elasticsearch version");
  }

  // makes sure we don't NPE
  @Test void check_fail_onNoContent() {
    storage.ensuredTemplates = true; // assume index templates called before

    server.enqueue(SUCCESS_RESPONSE); // empty instead of success response

    CheckResult result = storage.check();
    assertThat(result.ok()).isFalse();
    assertThat(result.error().getMessage())
      .isEqualTo("No content reading Elasticsearch version");
  }

  // TODO: when Armeria's mock server supports it, add a test for IOException

  @Test void check_unauthorized() {
    server.enqueue(RESPONSE_UNAUTHORIZED);

    CheckResult result = storage.check();
    assertThat(result.ok()).isFalse();
    assertThat(result.error().getMessage())
      .isEqualTo("User: anonymous is not authorized to perform: es:ESHttpGet");
  }

  /**
   * See {@link HttpCallTest#unprocessedRequest()} which shows {@link UnprocessedRequestException}
   * are re-wrapped as {@link RejectedExecutionException}.
   */
  @Test void isOverCapacity() {
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
  @Test void toStringContainsOnlySummaryInformation() {
    assertThat(storage).hasToString(
      String.format("ElasticsearchStorage{initialEndpoints=%s, index=zipkin}", server.httpUri()));
  }

  /** Ensure that Zipkin uses the legacy resource path when priority is not set. */
  @Test void check_create_legacy_indexTemplate_resourcePath_version78() {
    server.enqueue(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, "{\"version\":{\"number\":\"7.8.0\"}}"));
    server.enqueue(SUCCESS_RESPONSE); // get span template
    server.enqueue(SUCCESS_RESPONSE); // get dependency template
    server.enqueue(SUCCESS_RESPONSE); // get autocomplete template
    server.enqueue(SUCCESS_RESPONSE); // cluster health

    storage.check();

    server.takeRequest(); // get version

    assertThat(server.takeRequest().request().path()) // get span template
      .startsWith("/_template/zipkin-span_template");
    assertThat(server.takeRequest().request().path()) // // get dependency template
      .startsWith("/_template/zipkin-dependency_template");
    assertThat(server.takeRequest().request().path()) // get autocomplete template
      .startsWith("/_template/zipkin-autocomplete_template");
  }

  /**
   * Ensure that Zipkin uses the correct resource path of /_index_template when creating index
   * template for ES 7.8 when priority is set, as opposed to ES < 7.8 that uses /_template/
   */
  @Test void check_create_composable_indexTemplate_resourcePath_version78() {
    // Set up a new storage with priority
    storage.close();
    storage = newBuilder().templatePriority(0).build();

    server.enqueue(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, "{\"version\":{\"number\":\"7.8.0\"}}"));
    server.enqueue(SUCCESS_RESPONSE); // get span template
    server.enqueue(SUCCESS_RESPONSE); // get dependency template
    server.enqueue(SUCCESS_RESPONSE); // get autocomplete template
    server.enqueue(SUCCESS_RESPONSE); // cluster health

    storage.check();

    server.takeRequest(); // get version

    assertThat(server.takeRequest().request().path()) // get span template
      .startsWith("/_index_template/zipkin-span_template");
    assertThat(server.takeRequest().request().path()) // // get dependency template
      .startsWith("/_index_template/zipkin-dependency_template");
    assertThat(server.takeRequest().request().path()) // get autocomplete template
      .startsWith("/_index_template/zipkin-autocomplete_template");
  }

  /** Ensure that Zipkin uses the legacy resource path when priority is not set. */
  @Test void check_create_legacy_indexTemplate_resourcePath_version79() {
    server.enqueue(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, "{\"version\":{\"number\":\"7.9.0\"}}"));
    server.enqueue(SUCCESS_RESPONSE); // get span template
    server.enqueue(SUCCESS_RESPONSE); // get dependency template
    server.enqueue(SUCCESS_RESPONSE); // get autocomplete template
    server.enqueue(SUCCESS_RESPONSE); // cluster health

    storage.check();

    server.takeRequest(); // get version

    assertThat(server.takeRequest().request().path()) // get span template
      .startsWith("/_template/zipkin-span_template");
    assertThat(server.takeRequest().request().path()) // // get dependency template
      .startsWith("/_template/zipkin-dependency_template");
    assertThat(server.takeRequest().request().path()) // get autocomplete template
      .startsWith("/_template/zipkin-autocomplete_template");
  }

  /**
   * Ensure that Zipkin uses the correct resource path of /_index_template when creating index
   * template for ES 7.9 when priority is set, as opposed to ES < 7.8 that uses /_template/
   */
  @Test void check_create_composable_indexTemplate_resourcePath_version79() throws Exception {
    // Set up a new storage with priority
    storage.close();
    storage = newBuilder().templatePriority(0).build();

    server.enqueue(AggregatedHttpResponse.of(
      HttpStatus.OK, MediaType.JSON_UTF_8, "{\"version\":{\"number\":\"7.9.0\"}}"));
    server.enqueue(SUCCESS_RESPONSE); // get span template
    server.enqueue(SUCCESS_RESPONSE); // get dependency template
    server.enqueue(SUCCESS_RESPONSE); // get autocomplete template
    server.enqueue(SUCCESS_RESPONSE); // cluster health

    storage.check();

    server.takeRequest(); // get version

    assertThat(server.takeRequest().request().path()) // get span template
      .startsWith("/_index_template/zipkin-span_template");
    assertThat(server.takeRequest().request().path()) // // get dependency template
      .startsWith("/_index_template/zipkin-dependency_template");
    assertThat(server.takeRequest().request().path()) // get autocomplete template
      .startsWith("/_index_template/zipkin-autocomplete_template");
  }

  ElasticsearchStorage.Builder newBuilder() {
    return ElasticsearchStorage.newBuilder(new LazyHttpClient() {
      @Override public WebClient get() {
        return WebClient.of(server.httpUri());
      }

      @Override public String toString() {
        return server.httpUri().toString();
      }
    });
  }
}
