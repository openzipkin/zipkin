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
package zipkin2.elasticsearch;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.Callback;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Kind;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.internal.Nullable;
import zipkin2.storage.SpanConsumer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static zipkin2.TestObjects.TODAY;

public class ElasticsearchSpanConsumerTest {
  static final Endpoint WEB_ENDPOINT = Endpoint.newBuilder().serviceName("web").build();
  static final Endpoint APP_ENDPOINT = Endpoint.newBuilder().serviceName("app").build();

  @Rule public MockWebServer es = new MockWebServer();

  ElasticsearchStorage storage = ElasticsearchStorage.newBuilder()
    .hosts(asList(es.url("").toString()))
    .autocompleteKeys(asList("environment"))
    .build();
  SpanConsumer spanConsumer;

  /** gets the index template so that each test doesn't have to */
  @Before public void ensureIndexTemplate() throws Exception {
    ensureIndexTemplates(storage);
    spanConsumer = storage.spanConsumer();
  }

  private void ensureIndexTemplates(ElasticsearchStorage storage) throws InterruptedException {
    es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"6.0.0\"}}"));
    es.enqueue(new MockResponse()); // get span template
    es.enqueue(new MockResponse()); // get dependency template
    es.enqueue(new MockResponse()); // get tags template
    storage.ensureIndexTemplates();
    es.takeRequest(); // get version
    es.takeRequest(); // get span template
    es.takeRequest(); // get dependency template
    es.takeRequest(); // get tags template
  }

  @After
  public void close() {
    storage.close();
  }

  @Test
  public void addsTimestamp_millisIntoJson() throws Exception {
    es.enqueue(new MockResponse());

    Span span =
      Span.newBuilder().traceId("20").id("20").name("get").timestamp(TODAY * 1000).build();

    accept(span);

    assertThat(es.takeRequest().getBody().readUtf8())
      .contains("\n{\"timestamp_millis\":" + TODAY + ",\"traceId\":");
  }

  @Test
  public void writesSpanNaturallyWhenNoTimestamp() throws Exception {
    es.enqueue(new MockResponse());

    Span span = Span.newBuilder().traceId("1").id("1").name("foo").build();
    accept(Span.newBuilder().traceId("1").id("1").name("foo").build());

    assertThat(es.takeRequest().getBody().readByteString().utf8())
      .contains("\n" + new String(SpanBytesEncoder.JSON_V2.encode(span), "UTF-8") + "\n");
  }

  @Test
  public void traceIsSearchableByServerServiceName() throws Exception {
    es.enqueue(new MockResponse());

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
    assertThat(es.takeRequest().getBody().readByteString().utf8())
      .contains("{\"timestamp_millis\":2")
      .contains("{\"timestamp_millis\":1");
  }

  @Test
  public void addsPipelineId() throws Exception {
    close();

    storage =
      ElasticsearchStorage.newBuilder()
        .hosts(asList(es.url("").toString()))
        .pipeline("zipkin")
        .build();
    ensureIndexTemplate();

    es.enqueue(new MockResponse());

    accept(Span.newBuilder().traceId("1").id("1").name("foo").build());

    RecordedRequest request = es.takeRequest();
    assertThat(request.getPath()).isEqualTo("/_bulk?pipeline=zipkin");
  }

  @Test
  public void dropsWhenBacklog() throws Exception {
    close();

    storage =
      ElasticsearchStorage.newBuilder()
        .hosts(asList(es.url("").toString()))
        .maxRequests(1)
        .build();
    ensureIndexTemplate();

    es.enqueue(new MockResponse().setBodyDelay(1, TimeUnit.SECONDS));

    final LinkedBlockingQueue<Object> q = new LinkedBlockingQueue<>();
    Callback<Void> callback =
      new Callback<Void>() {
        @Override
        public void onSuccess(@Nullable Void value) {
          q.add("success");
        }

        @Override
        public void onError(Throwable t) {
          q.add(t);
        }
      };
    // one request is delayed
    storage.spanConsumer().accept(asList(TestObjects.CLIENT_SPAN)).enqueue(callback);

    // synchronous requests fail on backlog
    try {
      storage.spanConsumer().accept(asList(TestObjects.CLIENT_SPAN)).execute();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException e) {
    }

    // asynchronous requests fail on backlog
    storage.spanConsumer().accept(asList(TestObjects.CLIENT_SPAN)).enqueue(callback);

    assertThat(q.take()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void choosesTypeSpecificIndex() throws Exception {
    es.enqueue(new MockResponse());

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
    assertThat(es.takeRequest().getBody().readByteString().utf8())
      .startsWith("{\"index\":{\"_index\":\"zipkin:span-1971-01-01\",\"_type\":\"span\"");
  }

  /** Much simpler template which doesn't write the timestamp_millis field */
  @Test
  public void searchDisabled_simplerIndexTemplate() throws Exception {
    try (ElasticsearchStorage storage =
           ElasticsearchStorage.newBuilder()
             .hosts(this.storage.hostsSupplier().get())
             .searchEnabled(false)
             .build()) {

      es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"6.0.0\"}}"));
      es.enqueue(new MockResponse().setResponseCode(404)); // get span template
      es.enqueue(new MockResponse()); // put span template
      es.enqueue(new MockResponse()); // get dependency template
      es.enqueue(new MockResponse()); // get tags template
      storage.ensureIndexTemplates();
      es.takeRequest(); // get version
      es.takeRequest(); // get span template

      assertThat(es.takeRequest().getBody().readUtf8()) // put span template
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
  }

  /** Less overhead as a span json isn't rewritten to include a millis timestamp */
  @Test
  public void searchDisabled_doesntAddTimestampMillis() throws Exception {
    try (ElasticsearchStorage storage =
           ElasticsearchStorage.newBuilder()
             .hosts(this.storage.hostsSupplier().get())
             .searchEnabled(false)
             .build()) {

      ensureIndexTemplates(storage);
      es.enqueue(new MockResponse()); // for the bulk request

      Span span =
        Span.newBuilder().traceId("20").id("20").name("get").timestamp(TODAY * 1000).build();

      storage.spanConsumer().accept(asList(span)).execute();

      assertThat(es.takeRequest().getBody().readUtf8()).doesNotContain("timestamp_millis");
    }
  }

  @Test public void addsAutocompleteValue() throws Exception {
    es.enqueue(new MockResponse());

    accept(Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build());

    assertThat(es.takeRequest().getBody().readUtf8())
      .endsWith(""
        + "{\"index\":{\"_index\":\"zipkin:autocomplete-1970-01-01\",\"_type\":\"autocomplete\",\"_id\":\"environment=A\"}}\n"
        + "{\"tagKey\":\"environment\",\"tagValue\":\"A\"}\n");
  }

  @Test public void addsAutocompleteValue_suppressesWhenSameDay() throws Exception {
    es.enqueue(new MockResponse());
    es.enqueue(new MockResponse());

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    accept(s);
    accept(s.toBuilder().id(2).build());

    es.takeRequest(); // skip first
    // the tag is in the same date range as the other, so it should not write the tag again
    assertThat(es.takeRequest().getBody().readUtf8())
      .doesNotContain("autocomplete");
  }

  @Test public void addsAutocompleteValue_differentDays() throws Exception {
    es.enqueue(new MockResponse());
    es.enqueue(new MockResponse());

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    accept(s);
    accept(s.toBuilder().id(2).timestamp(1 + TimeUnit.DAYS.toMicros(1)).build());

    es.takeRequest(); // skip first
    // different day == different context
    assertThat(es.takeRequest().getBody().readUtf8())
      .endsWith(""
        + "{\"index\":{\"_index\":\"zipkin:autocomplete-1970-01-02\",\"_type\":\"autocomplete\",\"_id\":\"environment=A\"}}\n"
        + "{\"tagKey\":\"environment\",\"tagValue\":\"A\"}\n");
  }

  @Test public void addsAutocompleteValue_revertsSuppressionOnFailure() throws Exception {
    es.enqueue(new MockResponse().setResponseCode(500));
    es.enqueue(new MockResponse());

    Span s = Span.newBuilder().traceId("1").id("1").timestamp(1).putTag("environment", "A").build();
    try {
      accept(s);
      failBecauseExceptionWasNotThrown(IOException.class);
    } catch (IllegalStateException expected) {
    }
    accept(s);

    // We only cache when there was no error.. the second request should be same as the first
    assertThat(es.takeRequest().getBody().readUtf8())
      .isEqualTo(es.takeRequest().getBody().readUtf8());
  }

  void accept(Span... spans) throws Exception {
    spanConsumer.accept(asList(spans)).execute();
  }
}
