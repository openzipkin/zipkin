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

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Lazy;
import zipkin.internal.Util;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static zipkin.moshi.JsonReaders.collectValuesNamed;
import static zipkin.moshi.JsonReaders.enterPath;

/**
 * This is an http client based on Elasticsearch's rest api.
 *
 * <p>This currently uses elasticsearch classes to construct the queries, though this may change in
 * the future, particularly to support multiple versions of elasticsearch without classpath
 * conflicts.
 */
final class HttpClient extends InternalElasticsearchClient {
  static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  static final class Factory implements InternalElasticsearchClient.Factory {
    final Lazy<List<String>> hosts;
    final OkHttpClient client;
    final boolean flushOnWrites;
    final String pipeline;
    final int maxRequests;


    Factory(HttpClientBuilder builder) {
      this.hosts = builder.hosts;
      this.client = builder.client;
      this.flushOnWrites = builder.flushOnWrites;
      this.pipeline = builder.pipeline;
      this.maxRequests = builder.maxRequests;
    }

    @Override public InternalElasticsearchClient create(String allIndices) {
      return new HttpClient(this, allIndices);
    }

    @Override public String toString() {
      return new StringBuilder("{\"hosts\": [\"").append(Joiner.on("\", \"").join(hosts.get()))
          .append("\"]}").toString();
    }
  }

  final OkHttpClient http;
  final HttpUrl baseUrl;
  final boolean flushOnWrites;
  final String pipeline;

  final String[] allIndices;

  HttpClient(Factory f, String allIndices) {
    List<String> hosts = f.hosts.get();
    checkArgument(!hosts.isEmpty(), "no hosts configured");
    this.http = hosts.size() == 1
        ? f.client
        : f.client.newBuilder().dns(PseudoAddressRecordSet.create(hosts, f.client.dns())).build();
    this.http.dispatcher().setMaxRequests(f.maxRequests);
    this.http.dispatcher().setMaxRequestsPerHost(f.maxRequests);
    this.baseUrl = HttpUrl.parse(hosts.get(0));
    this.flushOnWrites = f.flushOnWrites;
    this.pipeline = f.pipeline;
    this.allIndices = new String[] {allIndices};
  }

  @Override protected String getVersion() {
    Request getNode = new Request.Builder().url(baseUrl).tag("get-node").build();

    try (Response response = http.newCall(getNode).execute()) {
      if (!response.isSuccessful()) {
        throw new IllegalStateException(response.body().string());
      }

      JsonReader version = enterPath(JsonReader.of(response.body().source()), "version", "number");
      if (version == null) throw new IllegalStateException(".version.number not in response");
      return version.nextString();
    } catch (IOException e) {
      throw new IllegalStateException("Could not get version", e);
    }
  }

  /**
   * This is a blocking call, used inside a lazy. That's because no writes should occur until the
   * template is available.
   */
  @Override protected void ensureTemplate(String name, String indexTemplate) {
    HttpUrl templateUrl = baseUrl.newBuilder("_template").addPathSegment(name).build();
    Request request = new Request.Builder().url(templateUrl).tag("get-template").build();

    try (Response response = http.newCall(request).execute()) {
      if (response.isSuccessful()) {
        return;
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not get " + templateUrl.encodedPath(), e);
    }

    Call putTemplate = http.newCall(new Request.Builder()
        .url(templateUrl)
        .put(RequestBody.create(APPLICATION_JSON, indexTemplate))
        .tag("update-template").build());

    try (Response response = putTemplate.execute()) {
      if (!response.isSuccessful()) {
        throw new IllegalStateException(response.body().string());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not put " + templateUrl.encodedPath(), e);
    }
  }

  /** This is a blocking call, only used in tests. */
  @Override protected void clear(String index) throws IOException {
    Request deleteRequest = new Request.Builder()
        .url(baseUrl.newBuilder().addPathSegment(index).build())
        .delete().tag("delete-index").build();

    try (Response response = http.newCall(deleteRequest).execute()) {
      if (!response.isSuccessful()) {
        throw new IllegalStateException("response failed: " + response);
      }
    }

    flush(index);
  }

  /** This is a blocking call, only used in tests. */
  void flush(String index) throws IOException {
    Request flushRequest = new Request.Builder()
        .url(baseUrl.newBuilder().addPathSegment(index).addPathSegment("_flush").build())
        .post(RequestBody.create(APPLICATION_JSON, ""))
        .tag("flush-index").build();

    try (Response response = http.newCall(flushRequest).execute()) {
      if (!response.isSuccessful()) {
        throw new IllegalStateException("response failed: " + response);
      }
    }
  }

  @Override
  protected ListenableFuture<List<String>> collectBucketKeys(String[] indices,
      QueryBuilder query, AbstractAggregationBuilder... aggregations) {
    SearchSourceBuilder elasticQuery = new SearchSourceBuilder().query(query).size(0);
    for (AbstractAggregationBuilder aggregation : aggregations) {
      elasticQuery.aggregation(aggregation);
    }

    Call searchRequest = http.newCall(new Request.Builder().url(lenientSearch(indices, SPAN))
        .post(RequestBody.create(APPLICATION_JSON, elasticQuery.toString()))
        .tag("search-spansAggregations").build());

    return new CallbackListenableFuture<List<String>>(searchRequest) {
      @Override List<String> convert(ResponseBody responseBody) throws IOException {
        Set<String> result = collectValuesNamed(JsonReader.of(responseBody.source()), "key");
        return Util.sortedList(result);
      }
    }.enqueue();
  }

  @Override protected ListenableFuture<List<Span>> findSpans(String[] indices, QueryBuilder query) {
    String body = new SearchSourceBuilder().query(query).size(MAX_RAW_ITEMS).toString();
    Call searchRequest = http.newCall(new Request.Builder().url(lenientSearch(indices, SPAN))
        .post(RequestBody.create(APPLICATION_JSON, body))
        .tag("search-spans").build());

    return new SearchResultFuture(searchRequest, ZipkinAdapters.SPAN_ADAPTER).enqueue();
  }

  @Override
  protected ListenableFuture<List<DependencyLink>> findDependencies(String[] indices) {
    QueryBuilder query = QueryBuilders.matchAllQuery();
    String body = new SearchSourceBuilder().query(query).size(MAX_RAW_ITEMS).toString();
    Call searchRequest =
        http.newCall(new Request.Builder().url(lenientSearch(indices, DEPENDENCY_LINK))
            .post(RequestBody.create(APPLICATION_JSON, body))
            .tag("search-dependencyLink").build());

    return new SearchResultFuture(searchRequest, ZipkinAdapters.DEPENDENCY_LINK_ADAPTER).enqueue();
  }

  @Override protected BulkSpanIndexer bulkSpanIndexer() {
    return new HttpBulkSpanIndexer(this, SPAN);
  }

  /** This is blocking so that we can determine if the cluster is healthy or not */
  @Override protected void ensureClusterReady(String catchAll) {
    Call getHealth = http.newCall(
        new Request.Builder().url(baseUrl.resolve("/_cluster/health/" + catchAll))
            .tag("get-cluster-health").build());

    try (Response response = getHealth.execute()) {
      if (response.isSuccessful()) {
        JsonReader status = enterPath(JsonReader.of(response.body().source()), "status");
        checkState(status != null, "Health status couldn't be read %s", response);
        checkState(!"RED".equalsIgnoreCase(status.nextString()), "Health status is RED");
      }
    } catch (IOException e) {
      throw new IllegalStateException("could not get " + getHealth.request().url(), e);
    }
  }

  @Override public void close() {
    // don't close the client because we didn't create it!
  }

  /** Matches the behavior of {@link IndicesOptions#lenientExpandOpen()} */
  HttpUrl lenientSearch(String[] indices, String type) {
    return baseUrl.newBuilder()
        .addPathSegment(Joiner.on(',').join(indices))
        .addPathSegment(type)
        .addPathSegment("_search")
        // keep these in alphabetical order as it simplifies amazon signatures!
        .addQueryParameter("allow_no_indices", "true")
        .addQueryParameter("expand_wildcards", "open")
        .addQueryParameter("ignore_unavailable", "true").build();
  }

  static final class SearchResultFuture<T> extends CallbackListenableFuture<List<T>> {
    final JsonAdapter<T> adapter;

    SearchResultFuture(Call searchRequest, JsonAdapter<T> adapter) {
      super(searchRequest);
      this.adapter = adapter;
    }

    @Override List<T> convert(ResponseBody responseBody) throws IOException {
      JsonReader hits = enterPath(JsonReader.of(responseBody.source()), "hits", "hits");
      if (hits == null || hits.peek() != JsonReader.Token.BEGIN_ARRAY) return null;

      List<T> result = new ArrayList<>();
      hits.beginArray();
      while (hits.hasNext()) {
        JsonReader source = enterPath(hits, "_source");
        if (source != null) {
          result.add(adapter.fromJson(source));
        }
        hits.endObject();
      }
      hits.endArray();
      return result.isEmpty() ? null : result;
    }
  }
}
