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
package zipkin.storage.elasticsearch.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Codec;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.CallbackCaptor;
import zipkin.internal.Span2;
import zipkin.internal.Span2Converter;
import zipkin.internal.v2.codec.MessageEncoder;
import zipkin.internal.v2.codec.Decoder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.TestObjects.TODAY;
import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.Util.UTF_8;
import static zipkin.storage.elasticsearch.http.ElasticsearchHttpSpanConsumer.prefixWithTimestampMillisAndQuery;

public class ElasticsearchHttpSpanConsumerTest {
  @Rule public MockWebServer es = new MockWebServer();

  ElasticsearchHttpStorage storage = ElasticsearchHttpStorage.builder()
    .hosts(asList(es.url("").toString()))
    .build();

  /** gets the index template so that each test doesn't have to */
  @Before
  public void ensureIndexTemplate() throws Exception {
    es.enqueue(new MockResponse().setBody("{\"version\":{\"number\":\"6.0.0\"}}"));
    es.enqueue(new MockResponse()); // get span template
    es.enqueue(new MockResponse()); // get dependency template
    storage.ensureIndexTemplates();
    es.takeRequest(); // get version
    es.takeRequest(); // get span template
    es.takeRequest(); // get dependency template
  }

  @After
  public void close() throws IOException {
    storage.close();
  }

  @Test public void addsTimestamp_millisIntoJson() throws Exception {
    es.enqueue(new MockResponse());

    Span span = Span.builder().traceId(20L).id(20L).name("get")
      .timestamp(TODAY * 1000).build();

    accept(span);

    assertThat(es.takeRequest().getBody().readUtf8())
      .contains("\n{\"timestamp_millis\":" + Long.toString(TODAY) + ",\"traceId\":");
  }

  @Test public void prefixWithTimestampMillisAndQuery_skipsWhenNoData() throws Exception {
    Span span = Span.builder().traceId(20L).id(22L).name("").parentId(21L).timestamp(0L)
      .addAnnotation(Annotation.create(0, CLIENT_SEND, TestObjects.WEB_ENDPOINT))
      .build();

    byte[] result = prefixWithTimestampMillisAndQuery(
      Span2Converter.fromSpan(span).get(0),
      span.timestamp
    );

    assertThat(new String(result, UTF_8))
      .startsWith("{\"traceId\":\"");
  }

  @Test public void prefixWithTimestampMillisAndQuery_addsTimestampMillis() throws Exception {
    Span span = Span.builder().traceId(20L).id(22L).name("").parentId(21L).timestamp(1L)
      .addAnnotation(Annotation.create(1L, CLIENT_SEND, TestObjects.WEB_ENDPOINT))
      .build();

    byte[] result = prefixWithTimestampMillisAndQuery(
      Span2Converter.fromSpan(span).get(0),
      span.timestamp
    );

    assertThat(new String(result, UTF_8))
      .startsWith("{\"timestamp_millis\":1,\"traceId\":");
  }

  @Test public void prefixWithTimestampMillisAndQuery_addsAnnotationQuery() throws Exception {
    Span span = Span.builder().traceId(20L).id(22L).name("").parentId(21L)
      .addAnnotation(Annotation.create(1L, "\"foo", TestObjects.WEB_ENDPOINT))
      .build();

    byte[] result = prefixWithTimestampMillisAndQuery(
      Span2Converter.fromSpan(span).get(0),
      span.timestamp
    );

    assertThat(new String(result, UTF_8))
      .startsWith("{\"_q\":[\"\\\"foo\"],\"traceId");
  }

  @Test public void prefixWithTimestampMillisAndQuery_addsAnnotationQueryTags() throws Exception {
    Span span = Span.builder().traceId(20L).id(22L).name("").parentId(21L)
      .addBinaryAnnotation(BinaryAnnotation.create("\"foo", "\"bar", TestObjects.WEB_ENDPOINT))
      .build();

    byte[] result = prefixWithTimestampMillisAndQuery(Span2Converter.fromSpan(span).get(0),
      span.timestamp
    );

    assertThat(new String(result, UTF_8))
      .startsWith("{\"_q\":[\"\\\"foo\",\"\\\"foo=\\\"bar\"],\"traceId");
  }

  @Test public void prefixWithTimestampMillisAndQuery_readable() throws Exception {
    Span span = Span.builder().traceId(20L).id(20L).name("get")
      .timestamp(TODAY * 1000).build();
    Span2 span2 = Span2Converter.fromSpan(span).get(0);

    byte[] message = MessageEncoder.JSON_BYTES.encode(asList(
      prefixWithTimestampMillisAndQuery(span2, span.timestamp)
    ));

    assertThat(Decoder.JSON.decodeList(message))
      .containsOnly(span2); // ignores timestamp_millis field
  }

  @Test public void doesntWriteSpanId() throws Exception {
    es.enqueue(new MockResponse());

    accept(TestObjects.LOTS_OF_SPANS[0]);

    RecordedRequest request = es.takeRequest();
    assertThat(request.getBody().readByteString().utf8())
      .doesNotContain("\"_type\":\"span\",\"_id\"");
  }

  @Test public void writesSpanNaturallyWhenNoTimestamp() throws Exception {
    es.enqueue(new MockResponse());

    Span span = Span.builder().traceId(1L).id(1L).name("foo").build();
    accept(span);

    assertThat(es.takeRequest().getBody().readByteString().utf8())
      .contains("\n" + new String(Codec.JSON.writeSpan(span), UTF_8) + "\n");
  }

  @Test public void traceIsSearchableBySRServiceName() throws Exception {
    es.enqueue(new MockResponse());

    Span clientSpan = Span.builder().traceId(20L).id(22L).name("").parentId(21L).timestamp(0L)
      .addAnnotation(Annotation.create(0, CLIENT_SEND, TestObjects.WEB_ENDPOINT))
      .build();

    Span serverSpan = Span.builder().traceId(20L).id(22L).name("get").parentId(21L)
      .addAnnotation(Annotation.create(1000, SERVER_RECV, TestObjects.APP_ENDPOINT))
      .build();

    accept(serverSpan, clientSpan);

    // make sure that both timestamps are in the index
    assertThat(es.takeRequest().getBody().readByteString().utf8())
      .contains("{\"timestamp_millis\":1")
      .contains("{\"timestamp_millis\":0");
  }

  @Test public void addsPipelineId() throws Exception {
    close();

    storage = ElasticsearchHttpStorage.builder()
      .hosts(asList(es.url("").toString()))
      .pipeline("zipkin")
      .build();
    ensureIndexTemplate();

    es.enqueue(new MockResponse());

    accept(TestObjects.TRACE.get(0));

    RecordedRequest request = es.takeRequest();
    assertThat(request.getPath())
      .isEqualTo("/_bulk?pipeline=zipkin");
  }

  @Test public void choosesTypeSpecificIndex() throws Exception {
    es.enqueue(new MockResponse());

    Annotation foo = Annotation.create(
      TimeUnit.DAYS.toMicros(365), // 1971-01-01
      "foo",
      TestObjects.APP_ENDPOINT
    );

    Span span = Span.builder().traceId(1L).id(2L).parentId(1L).name("s").addAnnotation(foo).build();

    // sanity check data
    assertThat(span.timestamp).isNull();
    assertThat(guessTimestamp(span)).isNull();

    accept(span);

    // index timestamp is the server timestamp, not current time!
    assertThat(es.takeRequest().getBody().readByteString().utf8()).contains(
      "{\"index\":{\"_index\":\"zipkin:span-1971-01-01\",\"_type\":\"span\"}}"
    );
  }

  void accept(Span... spans) throws Exception {
    CallbackCaptor<Void> callback = new CallbackCaptor<>();
    storage.asyncSpanConsumer().accept(asList(spans), callback);
    callback.get();
  }
}
