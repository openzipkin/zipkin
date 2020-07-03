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

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Kind;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.SpanConsumer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.TestObjects.UTF_8;

class ElasticsearchSpanConsumerTest {
  static final Endpoint WEB_ENDPOINT = Endpoint.newBuilder().serviceName("web").build();
  static final Endpoint APP_ENDPOINT = Endpoint.newBuilder().serviceName("app").build();

  final AggregatedHttpResponse SUCCESS_RESPONSE =
    AggregatedHttpResponse.of(ResponseHeaders.of(HttpStatus.OK), HttpData.empty());

  @RegisterExtension static MockWebServerExtension server = new MockWebServerExtension();

  ElasticsearchStorage storage;
  SpanConsumer spanConsumer;

  @BeforeEach void setUp() throws Exception {
    storage = ElasticsearchStorage.newBuilder(() -> WebClient.of(server.httpUri()))
      .autocompleteKeys(asList("environment"))
      .build();

    ensureIndexTemplate();
  }

  @AfterEach void tearDown() throws IOException {
    storage.close();
  }

  void ensureIndexTemplate() throws Exception {
    // gets the index template so that each test doesn't have to
    ensureIndexTemplates(storage);
    spanConsumer = storage.spanConsumer();
  }

  private void ensureIndexTemplates(ElasticsearchStorage storage) throws InterruptedException {
    server.enqueue(AggregatedHttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8,
      "{\"version\":{\"number\":\"6.0.0\"}}"));
    server.enqueue(SUCCESS_RESPONSE); // get span template
    server.enqueue(SUCCESS_RESPONSE); // get dependency template
    server.enqueue(SUCCESS_RESPONSE); // get tags template
    storage.ensureIndexTemplates();
    server.takeRequest(); // get version
    server.takeRequest(); // get span template
    server.takeRequest(); // get dependency template
    server.takeRequest(); // get tags template
  }

  @Test void addsTimestamp_millisIntoJson() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    Span span =
      Span.newBuilder().traceId("20").id("20").name("get").timestamp(TODAY * 1000).build();

    accept(span);

    assertThat(server.takeRequest().request().contentUtf8())
      .contains("\n{\"timestamp_millis\":" + TODAY + ",\"traceId\":");
  }

  @Test void writesSpanNaturallyWhenNoTimestamp() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    Span span = Span.newBuilder().traceId("1").id("1").name("foo").build();
    accept(Span.newBuilder().traceId("1").id("1").name("foo").build());

    assertThat(server.takeRequest().request().contentUtf8())
      .contains("\n" + new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8) + "\n");
  }

  @Test void traceIsSearchableByServerServiceName() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    Span clientSpan =
      Span.newBuilder()
        .traceId("20")
        .id("22")
        .name("")
        .parentId("21")
        .timestamp(1000L)
        .kind(Kind.CLIENT)
        .localEndpoint(WEB_ENDPOINT)
        .build();

    Span serverSpan =
      Span.newBuilder()
        .traceId("20")
        .id("22")
        .name("get")
        .parentId("21")
        .timestamp(2000L)
        .kind(Kind.SERVER)
        .localEndpoint(APP_ENDPOINT)
        .build();

    accept(serverSpan, clientSpan);

    // make sure that both timestamps are in the index
    assertThat(server.takeRequest().request().contentUtf8())
      .contains("{\"timestamp_millis\":2")
      .contains("{\"timestamp_millis\":1");
  }

  @Test void addsPipelineId() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> WebClient.of(server.httpUri()))
      .pipeline("zipkin")
      .build();
    ensureIndexTemplate();

    server.enqueue(SUCCESS_RESPONSE);

    accept(Span.newBuilder().traceId("1").id("1").name("foo").build());

    AggregatedHttpRequest request = server.takeRequest().request();
    assertThat(request.path()).isEqualTo("/_bulk?pipeline=zipkin");
  }

  @Test void choosesTypeSpecificIndex() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    Span span =
      Span.newBuilder()
        .traceId("1")
        .id("2")
        .parentId("1")
        .name("s")
        .localEndpoint(APP_ENDPOINT)
        .addAnnotation(TimeUnit.DAYS.toMicros(365) /* 1971-01-01 */, "foo")
        .build();

    // sanity check data
    assertThat(span.timestamp()).isNull();

    accept(span);

    // index timestamp is the server timestamp, not current time!
    assertThat(server.takeRequest().request().contentUtf8())
      .startsWith("{\"index\":{\"_index\":\"zipkin:span-1971-01-01\",\"_type\":\"span\"");
  }

  /** Much simpler template which doesn't write the timestamp_millis field */
  @Test void searchDisabled_simplerIndexTemplate() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> WebClient.of(server.httpUri()))
      .searchEnabled(false)
      .build();

    server.enqueue(AggregatedHttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8,
      "{\"version\":{\"number\":\"6.0.0\"}}"));
    server.enqueue(AggregatedHttpResponse.of(HttpStatus.NOT_FOUND)); // get span template
    server.enqueue(SUCCESS_RESPONSE); // put span template
    server.enqueue(SUCCESS_RESPONSE); // get dependency template
    server.enqueue(SUCCESS_RESPONSE); // get tags template
    storage.ensureIndexTemplates();
    server.takeRequest(); // get version
    server.takeRequest(); // get span template

    assertThat(server.takeRequest().request().contentUtf8()) // put span template
      .contains(
        ""
          + "  \"mappings\": {\n"
          + "    \"span\": {\n"
          + "      \"properties\": {\n"
          + "        \"traceId\": { \"type\": \"keyword\", \"norms\": false },\n"
          + "        \"annotations\": { \"enabled\": false },\n"
          + "        \"tags\": { \"enabled\": false }\n"
          + "      }\n"
          + "    }\n"
          + "  }\n");
  }

  /** Less overhead as a span json isn't rewritten to include a millis timestamp */
  @Test
  void searchDisabled_doesntAddTimestampMillis() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> WebClient.of(server.httpUri()))
      .searchEnabled(false)
      .build();
    ensureIndexTemplates(storage);
    server.enqueue(SUCCESS_RESPONSE); // for the bulk request

    Span span =
      Span.newBuilder().traceId("20").id("20").name("get").timestamp(TODAY * 1000).build();

    storage.spanConsumer().accept(asList(span)).execute();

    assertThat(server.takeRequest().request().contentUtf8()).doesNotContain("timestamp_millis");
  }

  @Test void addsAutocompleteValue() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);

    accept(Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build());

    assertThat(server.takeRequest().request().contentUtf8())
      .endsWith(""
        + "{\"index\":{\"_index\":\"zipkin:autocomplete-1970-01-01\",\"_type\":\"autocomplete\",\"_id\":\"environment=A\"}}\n"
        + "{\"tagKey\":\"environment\",\"tagValue\":\"A\"}\n");
  }

  @Test void addsAutocompleteValue_suppressesWhenSameDay() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);
    server.enqueue(SUCCESS_RESPONSE);

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    accept(s);
    accept(s.toBuilder().id(2).build());

    server.takeRequest(); // skip first
    // the tag is in the same date range as the other, so it should not write the tag again
    assertThat(server.takeRequest().request().contentUtf8())
      .doesNotContain("autocomplete");
  }

  @Test void addsAutocompleteValue_differentDays() throws Exception {
    server.enqueue(SUCCESS_RESPONSE);
    server.enqueue(SUCCESS_RESPONSE);

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    accept(s);
    accept(s.toBuilder().id(2).timestamp(1 + TimeUnit.DAYS.toMicros(1)).build());

    server.takeRequest(); // skip first
    // different day == different context
    assertThat(server.takeRequest().request().contentUtf8())
      .endsWith(""
        + "{\"index\":{\"_index\":\"zipkin:autocomplete-1970-01-02\",\"_type\":\"autocomplete\",\"_id\":\"environment=A\"}}\n"
        + "{\"tagKey\":\"environment\",\"tagValue\":\"A\"}\n");
  }

  @Test void addsAutocompleteValue_revertsSuppressionOnFailure() throws Exception {
    server.enqueue(AggregatedHttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR));
    server.enqueue(SUCCESS_RESPONSE);

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    try {
      accept(s);
      failBecauseExceptionWasNotThrown(RuntimeException.class);
    } catch (RuntimeException expected) {
    }
    accept(s);

    // We only cache when there was no error.. the second request should be same as the first
    assertThat(server.takeRequest().request().contentUtf8())
      .isEqualTo(server.takeRequest().request().contentUtf8());
  }

  void accept(Span... spans) throws Exception {
    spanConsumer.accept(asList(spans)).execute();
  }
}
