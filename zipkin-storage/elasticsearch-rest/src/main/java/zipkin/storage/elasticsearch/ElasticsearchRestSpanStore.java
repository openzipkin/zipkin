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

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Util;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;

public final class ElasticsearchRestSpanStore implements AsyncSpanStore {
  /**
   * The maximum count of raw spans returned in a trace query.
   *
   * <p>Not configurable as it implies adjustments to the index template (index.max_result_window)
   * and user settings
   *
   * <p> See https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html
   */
  static final int MAX_RAW_SPANS = 10000; // the default elasticsearch allowed limit

  final AsyncSpanStore delegate;
  final OkHttpClient client;
  final HttpUrl baseUrl;
  final IndexNameFormatter indexNameFormatter;
  final JsonAdapter<SearchResponse<Span>> searchSpansResponseAdapter;

  ElasticsearchRestSpanStore(AsyncSpanStore delegate, OkHttpClient client, HttpUrl baseUrl,
      Moshi moshi, IndexNameFormatter indexNameFormatter) {
    this.delegate = delegate;
    this.client = client;
    this.baseUrl = baseUrl;
    this.indexNameFormatter = indexNameFormatter;
    this.searchSpansResponseAdapter = moshi.adapter(
        Types.newParameterizedType(SearchResponse.class, Span.class));
  }

  @Override
  public void getTraces(QueryRequest queryRequest, Callback<List<List<Span>>> callback) {
    delegate.getTraces(queryRequest, callback);
  }

  @Override public void getTrace(long traceId, Callback<List<Span>> callback) {
    delegate.getTrace(traceId, callback);
  }

  @Override public void getRawTrace(long traceId, Callback<List<Span>> callback) {
    Request searchRequest = new Request.Builder().url(
        baseUrl.newBuilder("/" + indexNameFormatter.catchAll() + "/span/_search")
            .addQueryParameter("q", "traceId:" + Util.toLowerHex(traceId))
            .addQueryParameter("size", String.valueOf(MAX_RAW_SPANS)).build()).build();

    client.newCall(searchRequest).enqueue(new okhttp3.Callback() {
      @Override public void onFailure(Call call, IOException e) {
        callback.onError(e);
      }

      @Override public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          callback.onError(new IllegalStateException("unsuccessful response: " + response));
          return;
        }
        SearchResponse<Span> searchResponse = searchSpansResponseAdapter.fromJson(response.body().source());
        if (searchResponse.hits.total == 0) {
          callback.onSuccess(null);
        } else {
          List<Span> trace = new ArrayList<Span>(searchResponse.hits.total);
          for (SearchHit<Span> hit : searchResponse.hits.hits) {
            trace.add(hit._source);
          }
          callback.onSuccess(trace);
        }
      }
    });
  }

  @Override public void getServiceNames(Callback<List<String>> callback) {
    delegate.getServiceNames(callback);
  }

  @Override public void getSpanNames(String serviceName, Callback<List<String>> callback) {
    delegate.getSpanNames(serviceName, callback);
  }

  @Override
  public void getDependencies(long endTs, Long lookback, Callback<List<DependencyLink>> callback) {
    delegate.getDependencies(endTs, lookback, callback);
  }

  static final class SearchResponse<T> {
    SearchHits<T> hits;
  }

  static final class SearchHits<T> {
    int total;
    List<SearchHit<T>> hits;
  }

  static final class SearchHit<T> {
    T _source;
  }
}
