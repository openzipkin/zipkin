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
package zipkin.storage.elasticsearch.http.integration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Span;
import zipkin.internal.Util;
import zipkin.internal.V2SpanConverter;
import zipkin.storage.elasticsearch.http.ElasticsearchHttpStorage;
import zipkin.storage.elasticsearch.http.InternalForTests;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.TODAY;
import static zipkin.TestObjects.WEB_ENDPOINT;
import static zipkin.storage.elasticsearch.http.integration.LazyElasticsearchHttpStorage.INDEX;

abstract class ElasticsearchHttpSpanConsumerTest {
  // we assume that buckets use a simple daily format.
  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

  ElasticsearchHttpSpanConsumerTest() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  /** Should maintain state between multiple calls within a test. */
  abstract ElasticsearchHttpStorage storage();

  /** Clears store between tests. */
  @Before
  public void clear() throws IOException {
    InternalForTests.clear(storage());
  }

  @Test
  public void spanGoesIntoADailyIndex_whenTimestampIsDerived() throws Exception {
    long twoDaysAgo = (TODAY - 2 * DAY);

    Span span = Span.builder().traceId(20L).id(20L).name("get")
      .addAnnotation(Annotation.create(twoDaysAgo * 1000, SERVER_RECV, WEB_ENDPOINT))
      .addAnnotation(Annotation.create(TODAY * 1000, SERVER_SEND, WEB_ENDPOINT))
      .build();

    accept(span);

    // make sure the span went into an index corresponding to its first annotation timestamp
    assertThat(findSpans(twoDaysAgo, span.traceId))
      .contains("\"hits\":{\"total\":1");
  }

  String findSpans(long endTs, long traceId) throws IOException {
    return new OkHttpClient().newCall(new Request.Builder().url(
      HttpUrl.parse(baseUrl()).newBuilder()
        .addPathSegment(INDEX + ":span-" + dateFormat.format(new Date(endTs)))
        .addPathSegment("_search")
        .addQueryParameter("q", "traceId:" + Util.toLowerHex(traceId)).build())
      .get().build()).execute().body().string();
  }

  @Test
  public void spanGoesIntoADailyIndex_whenTimestampIsExplicit() throws Exception {
    long twoDaysAgo = (TODAY - 2 * DAY);

    Span span = Span.builder().traceId(20L).id(20L).name("get")
      .timestamp(twoDaysAgo * 1000).build();

    accept(span);

    // make sure the span went into an index corresponding to its timestamp, not collection time
    assertThat(findSpans(twoDaysAgo, span.traceId))
      .contains("\"hits\":{\"total\":1");
  }

  @Test
  public void spanGoesIntoADailyIndex_fallsBackToTodayWhenNoTimestamps() throws Exception {
    Span span = Span.builder().traceId(20L).id(20L).name("get").build();

    accept(span);

    // make sure the span went into an index corresponding to collection time
    assertThat(findSpans(TODAY, span.traceId))
      .contains("\"hits\":{\"total\":1");
  }

  @Test
  public void searchByTimestampMillis() throws Exception {
    Span span = Span.builder().timestamp(TODAY * 1000).traceId(20L).id(20L).name("get").build();

    accept(span);

    Call searchRequest = new OkHttpClient().newCall(new Request.Builder().url(
      HttpUrl.parse(baseUrl()).newBuilder()
        .addPathSegment(INDEX + ":span-*")
        .addPathSegment("_search")
        .addQueryParameter("q", "timestamp_millis:" + TODAY).build())
      .get().tag("search-terms").build());

    assertThat(searchRequest.execute().body().string())
      .contains("\"hits\":{\"total\":1");
  }

  abstract String baseUrl();

  void accept(Span span) throws Exception {
    storage().spanConsumer().accept(V2SpanConverter.fromSpan(span)).execute();
  }
}
