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

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import zipkin.Component;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.Callback;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;
import zipkin.storage.elasticsearch.http.internal.client.HttpCall;
import zipkin.storage.elasticsearch.http.internal.client.SearchCallFactory;

import static java.util.Arrays.asList;

@Deprecated
final class HttpClient extends InternalElasticsearchClient {
  static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  static final class Factory implements InternalElasticsearchClient.Factory {
    final ElasticsearchHttpStorage.Builder delegate;

    Factory(HttpClientBuilder delegate) {
      this.delegate = delegate.builder;
    }

    @Override public InternalElasticsearchClient create(String allIndices) {
      return new HttpClient(this, allIndices);
    }
  }

  final ElasticsearchHttpStorage delegate;
  final HttpCall.Factory http;
  final SearchCallFactory search;
  final ElasticsearchHttpSpanStore spanStore;
  final String[] allIndices;

  HttpClient(Factory f, String allIndices) {
    delegate = f.delegate.build();
    http = delegate.http();
    search = new SearchCallFactory(http);
    spanStore = new ElasticsearchHttpSpanStore(delegate);
    this.allIndices = new String[] {allIndices};
  }

  @Override protected String getVersion() {
    return VersionSpecificTemplate.getVersion(http);
  }

  @Override protected void ensureTemplate(String name, String indexTemplate) {
    EnsureIndexTemplate.apply(http, name, indexTemplate);
  }

  /** This is a blocking call, only used in tests. */
  @Override protected void clear(String index) throws IOException {
    delegate.clear(index);
  }

  @Override
  protected ListenableFuture<List<String>> collectBucketKeys(String[] indices,
      QueryBuilder query, AbstractAggregationBuilder... aggregations) {
    SearchSourceBuilder elasticQuery = new SearchSourceBuilder().query(query).size(0);
    for (AbstractAggregationBuilder aggregation : aggregations) {
      elasticQuery.aggregation(aggregation);
    }

    HttpCall<List<String>> searchRequest =
        http.newCall(new Request.Builder().url(lenientSearch(indices, SPAN))
            .post(RequestBody.create(APPLICATION_JSON, elasticQuery.toString()))
            .tag("search-spansAggregations").build(), BodyConverters.SORTED_KEYS);

    CallbackListenableFuture<List<String>> result = new CallbackListenableFuture<>();
    searchRequest.submit(result);
    return result;
  }

  @Override protected ListenableFuture<List<Span>> findSpans(String[] indices, QueryBuilder query) {
    String body = new SearchSourceBuilder().query(query).size(MAX_RESULT_WINDOW).toString();

    HttpCall<List<Span>> searchRequest =
        http.newCall(new Request.Builder().url(lenientSearch(indices, SPAN))
            .post(RequestBody.create(APPLICATION_JSON, body))
            .tag("search-spans").build(), BodyConverters.NULLABLE_SPANS);

    CallbackListenableFuture<List<Span>> result = new CallbackListenableFuture<>();
    searchRequest.submit(result);
    return result;
  }

  @Override
  protected ListenableFuture<List<DependencyLink>> findDependencies(String[] indices) {
    CallbackListenableFuture<List<DependencyLink>> result = new CallbackListenableFuture<>();
    spanStore.getDependencies(asList(indices), result);
    return result;
  }

  @Override protected BulkSpanIndexer bulkSpanIndexer() {
    HttpBulkSpanIndexer result = new HttpBulkSpanIndexer(delegate);
    return new BulkSpanIndexer() {
      @Override public BulkSpanIndexer add(String index, Span span, Long timestampMillis) {
        result.add(index, span, timestampMillis);
        return this;
      }

      @Override public void execute(Callback<Void> callback) {
        result.execute(callback);
      }
    };
  }

  /** This is blocking so that we can determine if the cluster is healthy or not */
  @Override protected void ensureClusterReady(String catchAll) {
    Component.CheckResult result = delegate.ensureClusterReady(catchAll);
    if (result.exception != null) throw Throwables.propagate(result.exception);
  }

  @Override public void close() {
    delegate.close();
  }

  HttpUrl lenientSearch(String[] indices, String type) {
    return search.lenientSearch(asList(indices), type);
  }

  static final class CallbackListenableFuture<V> extends AbstractFuture<V> implements Callback<V> {
    @Override public void onSuccess(@Nullable V value) {
      set(value);
    }

    @Override public void onError(Throwable t) {
      setException(t);
    }
  }
}
