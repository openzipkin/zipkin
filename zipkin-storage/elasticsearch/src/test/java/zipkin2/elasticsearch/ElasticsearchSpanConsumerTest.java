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
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
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

public class ElasticsearchSpanConsumerTest {
  static final Endpoint WEB_ENDPOINT = Endpoint.newBuilder().serviceName("web").build();
  static final Endpoint APP_ENDPOINT = Endpoint.newBuilder().serviceName("app").build();

  final BlockingQueue<AggregatedHttpRequest> CAPTURED_REQUESTS = new LinkedBlockingQueue<>();
  final BlockingQueue<AggregatedHttpResponse> MOCK_RESPONSES = new LinkedBlockingQueue<>();
  final AggregatedHttpResponse SUCCESS_RESPONSE =
    AggregatedHttpResponse.of(ResponseHeaders.of(HttpStatus.OK), HttpData.EMPTY_DATA);

  @Rule public ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) throws Exception {
      sb.serviceUnder("/", (ctx, req) -> {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        req.aggregate().thenAccept(agg -> {
          CAPTURED_REQUESTS.add(agg);
          AggregatedHttpResponse response = MOCK_RESPONSES.remove();
          if (response.headers().contains("delay-one-second")) {
            ctx.eventLoop().schedule(() -> responseFuture.complete(HttpResponse.of(response)),
              1, TimeUnit.SECONDS);
          } else {
            responseFuture.complete(HttpResponse.of(response));
          }
        }).exceptionally(t -> {
          responseFuture.completeExceptionally(t);
          return null;
        });
        return HttpResponse.from(responseFuture);
      });
    }
  };

  ElasticsearchStorage storage;
  SpanConsumer spanConsumer;

  @Before public void setUp() throws Exception {
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .autocompleteKeys(asList("environment"))
      .build();

    ensureIndexTemplate();
  }

  @After public void tearDown() throws IOException {
    storage.close();
    assertThat(MOCK_RESPONSES).isEmpty();

    // Tests don't have to extract every request.
    CAPTURED_REQUESTS.clear();
  }

  void ensureIndexTemplate() throws Exception {
    // gets the index template so that each test doesn't have to
    ensureIndexTemplates(storage);
    spanConsumer = storage.spanConsumer();
  }

  private void ensureIndexTemplates(ElasticsearchStorage storage) throws InterruptedException {
    MOCK_RESPONSES.add(AggregatedHttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8,
      "{\"version\":{\"number\":\"6.0.0\"}}"));
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // get span template
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // get dependency template
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // get tags template
    storage.ensureIndexTemplates();
    CAPTURED_REQUESTS.take(); // get version
    CAPTURED_REQUESTS.take(); // get span template
    CAPTURED_REQUESTS.take(); // get dependency template
    CAPTURED_REQUESTS.take(); // get tags template
  }

  @Test public void addsTimestamp_millisIntoJson() throws Exception {
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

    Span span =
      Span.newBuilder().traceId("20").id("20").name("get").timestamp(TODAY * 1000).build();

    accept(span);

    assertThat(CAPTURED_REQUESTS.take().contentUtf8())
      .contains("\n{\"timestamp_millis\":" + TODAY + ",\"traceId\":");
  }

  @Test public void writesSpanNaturallyWhenNoTimestamp() throws Exception {
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

    Span span = Span.newBuilder().traceId("1").id("1").name("foo").build();
    accept(Span.newBuilder().traceId("1").id("1").name("foo").build());

    assertThat(CAPTURED_REQUESTS.take().contentUtf8())
      .contains("\n" + new String(SpanBytesEncoder.JSON_V2.encode(span), UTF_8) + "\n");
  }

  @Test public void traceIsSearchableByServerServiceName() throws Exception {
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

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
    assertThat(CAPTURED_REQUESTS.take().contentUtf8())
      .contains("{\"timestamp_millis\":2")
      .contains("{\"timestamp_millis\":1");
  }

  @Test public void addsPipelineId() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .pipeline("zipkin")
      .build();
    ensureIndexTemplate();

    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

    accept(Span.newBuilder().traceId("1").id("1").name("foo").build());

    AggregatedHttpRequest request = CAPTURED_REQUESTS.take();
    assertThat(request.path()).isEqualTo("/_bulk?pipeline=zipkin");
  }

  @Test public void choosesTypeSpecificIndex() throws Exception {
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

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
    assertThat(CAPTURED_REQUESTS.take().contentUtf8())
      .startsWith("{\"index\":{\"_index\":\"zipkin:span-1971-01-01\",\"_type\":\"span\"");
  }

  /** Much simpler template which doesn't write the timestamp_millis field */
  @Test public void searchDisabled_simplerIndexTemplate() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .searchEnabled(false)
      .build();

    MOCK_RESPONSES.add(AggregatedHttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8,
      "{\"version\":{\"number\":\"6.0.0\"}}"));
    MOCK_RESPONSES.add(AggregatedHttpResponse.of(HttpStatus.NOT_FOUND)); // get span template
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // put span template
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // get dependency template
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // get tags template
    storage.ensureIndexTemplates();
    CAPTURED_REQUESTS.take(); // get version
    CAPTURED_REQUESTS.take(); // get span template

    assertThat(CAPTURED_REQUESTS.take().contentUtf8()) // put span template
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
  public void searchDisabled_doesntAddTimestampMillis() throws Exception {
    storage.close();
    storage = ElasticsearchStorage.newBuilder(() -> HttpClient.of(server.httpUri("/")))
      .searchEnabled(false)
      .build();
    ensureIndexTemplates(storage);
    MOCK_RESPONSES.add(SUCCESS_RESPONSE); // for the bulk request

    Span span =
      Span.newBuilder().traceId("20").id("20").name("get").timestamp(TODAY * 1000).build();

    storage.spanConsumer().accept(asList(span)).execute();

    assertThat(CAPTURED_REQUESTS.take().contentUtf8()).doesNotContain("timestamp_millis");
  }

  @Test public void addsAutocompleteValue() throws Exception {
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

    accept(Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build());

    assertThat(CAPTURED_REQUESTS.take().contentUtf8())
      .endsWith(""
        + "{\"index\":{\"_index\":\"zipkin:autocomplete-1970-01-01\",\"_type\":\"autocomplete\",\"_id\":\"environment=A\"}}\n"
        + "{\"tagKey\":\"environment\",\"tagValue\":\"A\"}\n");
  }

  @Test public void addsAutocompleteValue_suppressesWhenSameDay() throws Exception {
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    accept(s);
    accept(s.toBuilder().id(2).build());

    CAPTURED_REQUESTS.take(); // skip first
    // the tag is in the same date range as the other, so it should not write the tag again
    assertThat(CAPTURED_REQUESTS.take().contentUtf8())
      .doesNotContain("autocomplete");
  }

  @Test public void addsAutocompleteValue_differentDays() throws Exception {
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    accept(s);
    accept(s.toBuilder().id(2).timestamp(1 + TimeUnit.DAYS.toMicros(1)).build());

    CAPTURED_REQUESTS.take(); // skip first
    // different day == different context
    assertThat(CAPTURED_REQUESTS.take().contentUtf8())
      .endsWith(""
        + "{\"index\":{\"_index\":\"zipkin:autocomplete-1970-01-02\",\"_type\":\"autocomplete\",\"_id\":\"environment=A\"}}\n"
        + "{\"tagKey\":\"environment\",\"tagValue\":\"A\"}\n");
  }

  @Test public void addsAutocompleteValue_revertsSuppressionOnFailure() throws Exception {
    MOCK_RESPONSES.add(AggregatedHttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR));
    MOCK_RESPONSES.add(SUCCESS_RESPONSE);

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    try {
      accept(s);
      failBecauseExceptionWasNotThrown(RuntimeException.class);
    } catch (RuntimeException expected) {
    }
    accept(s);

    // We only cache when there was no error.. the second request should be same as the first
    assertThat(CAPTURED_REQUESTS.take().contentUtf8())
      .isEqualTo(CAPTURED_REQUESTS.take().contentUtf8());
  }

  void accept(Span... spans) throws Exception {
    spanConsumer.accept(asList(spans)).execute();
  }
}
