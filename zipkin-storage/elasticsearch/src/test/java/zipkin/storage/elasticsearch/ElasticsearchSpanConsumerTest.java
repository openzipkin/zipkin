/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Before;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.TODAY;
import static zipkin.TestObjects.WEB_ENDPOINT;

public class ElasticsearchSpanConsumerTest {

  private final ElasticsearchStorage storage;

  public ElasticsearchSpanConsumerTest() {
    this.storage = ElasticsearchTestGraph.INSTANCE.storage.get();
  }

  @Before
  public void clear() {
    storage.clear();
  }

  @Test
  public void spanGoesIntoADailyIndex_whenTimestampIsDerived() {
    long twoDaysAgo = (TODAY - 2 * DAY);

    Span span = Span.builder().traceId(20L).id(20L).name("get")
        .addAnnotation(Annotation.create(twoDaysAgo * 1000, SERVER_RECV, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(TODAY * 1000, SERVER_SEND, WEB_ENDPOINT))
        .build();

    accept(span);

    SearchResponse indexFromTwoDaysAgo = storage.client()
        .prepareSearch(storage.indexNameFormatter.indexNameForTimestamp(twoDaysAgo))
        .setTypes(ElasticsearchConstants.SPAN)
        .get();

    // make sure the span went into an index corresponding to its first annotation timestamp
    assertThat(indexFromTwoDaysAgo.getHits().getTotalHits())
        .isEqualTo(1);
  }

  @Test
  public void spanGoesIntoADailyIndex_whenTimestampIsExplicit() {
    long twoDaysAgo = (TODAY - 2 * DAY);

    Span span = Span.builder().traceId(20L).id(20L).name("get")
        .timestamp(twoDaysAgo * 1000).build();

    accept(span);

    SearchResponse indexFromTwoDaysAgo = storage.client()
        .prepareSearch(storage.indexNameFormatter.indexNameForTimestamp(twoDaysAgo))
        .setTypes(ElasticsearchConstants.SPAN)
        .get();

    // make sure the span went into an index corresponding to its timestamp, not collection time
    assertThat(indexFromTwoDaysAgo.getHits().getTotalHits())
        .isEqualTo(1);
  }

  @Test
  public void spanGoesIntoADailyIndex_fallsBackToTodayWhenNoTimestamps() {
    Span span = Span.builder().traceId(20L).id(20L).name("get").build();

    accept(span);

    SearchResponse indexFromToday = storage.client()
        .prepareSearch(storage.indexNameFormatter.indexNameForTimestamp(TODAY))
        .setTypes(ElasticsearchConstants.SPAN)
        .get();

    // make sure the span went into an index corresponding to collection time
    assertThat(indexFromToday.getHits().getTotalHits())
        .isEqualTo(1);
  }

  @Test
  public void searchByTimestampMillis() {
    Span span = Span.builder().timestamp(TODAY * 1000).traceId(20L).id(20L).name("get").build();

    accept(span);

    SearchResponse indexFromToday = storage.client()
        .prepareSearch(storage.indexNameFormatter.indexNameForTimestamp(TODAY))
        .setTypes(ElasticsearchConstants.SPAN)
        .setQuery(termQuery("timestamp_millis", TODAY))
        .get();

    assertThat(indexFromToday.getHits().getTotalHits())
        .isEqualTo(1);
  }

  @Test
  public void prefixWithTimestampMillis() {
    Span span = Span.builder().traceId(20L).id(20L).name("get")
        .timestamp(TODAY * 1000).build();

    byte[] result =
        ElasticsearchSpanConsumer.prefixWithTimestampMillis(Codec.JSON.writeSpan(span), TODAY);

    String json = new String(result);
    assertThat(json)
        .startsWith("{\"timestamp_millis\":" + Long.toString(TODAY) + ",\"traceId\":");

    assertThat(Codec.JSON.readSpan(json.getBytes()))
        .isEqualTo(span); // ignores timestamp_millis field
  }

  void accept(Span span) {
    Futures.getUnchecked(storage.computeGuavaSpanConsumer().accept(ImmutableList.of(span)));
  }
}
