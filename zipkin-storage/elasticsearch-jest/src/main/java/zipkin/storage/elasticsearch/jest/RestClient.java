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
package zipkin.storage.elasticsearch.jest;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.JestResultHandler;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.cluster.Health;
import io.searchbox.core.Bulk;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.MetricAggregation;
import io.searchbox.core.search.aggregation.RootAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation;
import io.searchbox.indices.DeleteIndex;
import io.searchbox.indices.Flush;
import io.searchbox.indices.template.GetTemplate;
import io.searchbox.indices.template.PutTemplate;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.storage.elasticsearch.InternalElasticsearchClient;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.Futures.transform;

/**
 * An implementation of {@link InternalElasticsearchClient} that wraps a {@link
 * io.searchbox.client.JestClient}
 */
public final class RestClient extends InternalElasticsearchClient {

  /**
   * Because the {@link RestClient} is forced to send the index names in the URL, too many indices
   * (or long names) can quickly exceed the 4096-byte limit commonly imposed on a single HTTP line.
   * This limit is not entirely within the control of Elasticsearch, either, as it may be imposed by
   * arbitrary HTTP-speaking middleware along the request's route. Some more details can be found on
   * this "wontfix" issue: https://github.com/elastic/elasticsearch/issues/7298
   */
  private static final int MAX_INDICES = 100;

  public static final class Builder implements InternalElasticsearchClient.Builder {
    List<String> hosts = Collections.singletonList("http://localhost:9200");
    boolean flushOnWrites;

    /**
     * Unnecessary, but we'll check it's not null for consistency's sake.
     */
    @Override public Builder cluster(String cluster) {
      checkNotNull(cluster, "cluster");
      return this;
    }

    /**
     * A comma separated list of elasticsearch hostnodes to connect to, in http://host:port or
     * https://host:port format. Defaults to "http://localhost:9200".
     */
    @Override public Builder hosts(List<String> hosts) {
      this.hosts = checkNotNull(hosts, "hosts");
      return this;
    }

    @Override public Builder flushOnWrites(boolean flushOnWrites) {
      this.flushOnWrites = flushOnWrites;
      return this;
    }

    @Override public Factory buildFactory() {
      return new Factory(this);
    }

    public Builder() {
    }
  }

  private static final class Factory implements InternalElasticsearchClient.Factory {
    final List<String> hosts;
    final boolean flushOnWrites;

    Factory(Builder builder) {
      this.hosts = ImmutableList.copyOf(builder.hosts);
      this.flushOnWrites = builder.flushOnWrites;
    }

    @Override public InternalElasticsearchClient create(String allIndices) {
      return new RestClient(this, allIndices);
    }
  }

  @VisibleForTesting final JestClient client;
  private final String[] allIndices;
  private final boolean flushOnWrites;

  private RestClient(Factory f, String allIndices) {
    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(new HttpClientConfig.Builder(f.hosts)
        .defaultMaxTotalConnectionPerRoute(6) // matches "regular" TransportClient node conns
        .maxTotalConnection(6 * 10) // would be 20 otherwise, or ~3 routes
        .connTimeout(Ints.checkedCast(TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS)))
        .readTimeout(Ints.checkedCast(TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS)))
        .multiThreaded(true)
        .gson(
            new GsonBuilder()
                .registerTypeAdapter(Span.class, SpanDeserializer.INSTANCE)
                .registerTypeAdapter(DependencyLink.class, DependencyLinkDeserializer.INSTANCE)
                .create())
        .build());
    this.client = factory.getObject();
    this.flushOnWrites = f.flushOnWrites;
    this.allIndices = new String[] {allIndices};
  }

  @Override
  public void ensureTemplate(String name, String indexTemplate) {
    JestResult existingTemplate = getUnchecked(toGuava(new GetTemplate.Builder(name).build()));
    if (existingTemplate.isSucceeded()) {
      return;
    }
    PutTemplate action = new PutTemplate.Builder(name, indexTemplate).build();
    executeUnchecked(action);
  }

  @Override
  public void clear(String index) {
    executeUnchecked(new DeleteIndex.Builder(index).build());
    executeUnchecked(new Flush.Builder().addIndex(index).build());
  }

  @Override
  public ListenableFuture<Buckets> scanTraces(String[] indices, QueryBuilder query,
      AbstractAggregationBuilder... aggregations) {
    if (indices.length > MAX_INDICES) {
      query = QueryBuilders.indicesQuery(query, indices).noMatchQuery("none");
      indices = allIndices;
    }
    SearchSourceBuilder elasticQuery = new SearchSourceBuilder().query(query).size(0);

    for (AbstractAggregationBuilder aggregation : aggregations) {
      elasticQuery.aggregation(aggregation);
    }

    return transform(toGuava(
        new LenientSearch(new Search.Builder(elasticQuery.toString())
            .addIndex(Arrays.asList(indices))
            .addType(SPAN))
    ), AsRestBuckets.INSTANCE);
  }

  private enum AsRestBuckets implements Function<SearchResult, Buckets> {
    INSTANCE;

    @Override public Buckets apply(SearchResult input) {
      return new RestBuckets(input);
    }
  }

  @Override
  public ListenableFuture<List<Span>> findSpans(String[] indices, QueryBuilder query) {
    if (indices.length > MAX_INDICES) {
      query = QueryBuilders.indicesQuery(query, indices).noMatchQuery("none");
      indices = allIndices;
    }

    return transform(toGuava(
        new LenientSearch(new Search.Builder(new SearchSourceBuilder().query(query).size(
            InternalElasticsearchClient.MAX_RAW_SPANS).toString())
            .addIndex(Arrays.asList(indices))
            .addType(SPAN))), new Function<SearchResult, List<Span>>() {
      @Override public List<Span> apply(SearchResult input) {
        if (input.getTotal() == 0) return null;

        ImmutableList.Builder<Span> builder = ImmutableList.builder();
        for (SearchResult.Hit<Span, ?> hit : input.getHits(Span.class)) {
          builder.add(hit.source);
        }
        return builder.build();
      }
    });
  }

  @Override
  public ListenableFuture<Collection<DependencyLink>> findDependencies(String[] indices) {
    QueryBuilder query = QueryBuilders.matchAllQuery();
    if (indices.length > MAX_INDICES) {
      query = QueryBuilders.indicesQuery(query, indices).noMatchQuery("none");
      indices = allIndices;
    }

    Search.Builder search = new Search.Builder(new SearchSourceBuilder().query(query).toString())
        .addIndex(Arrays.asList(indices))
        .addType(DEPENDENCY_LINK);

    return transform(toGuava(new LenientSearch(search)),
        new Function<SearchResult, Collection<DependencyLink>>() {
          @Override public Collection<DependencyLink> apply(SearchResult input) {
            ImmutableList.Builder<DependencyLink> builder = ImmutableList.builder();
            for (SearchResult.Hit<DependencyLink, ?> hit : input.getHits(DependencyLink.class)) {
              builder.add(hit.source);
            }
            return builder.build();
          }
        });
  }

  @Override
  public ListenableFuture<Void> indexSpans(List<IndexableSpan> spans) {
    if (spans.isEmpty()) return Futures.immediateFuture(null);

    // Create a bulk request when there is more than one span to store
    ListenableFuture<?> future;
    final Set<String> indices = new HashSet<>();
    if (spans.size() == 1) {
      IndexableSpan span = getOnlyElement(spans);
      future = toGuava(toIndexRequest(span));
      if (flushOnWrites) {
        indices.add(span.index);
      }
    } else {
      Bulk.Builder batch = new Bulk.Builder();
      for (IndexableSpan span : spans) {
        batch.addAction(toIndexRequest(span));
        if (flushOnWrites) {
          indices.add(span.index);
        }
      }
      future = toGuava(batch.build());
    }

    if (flushOnWrites) {
      future = transform(future, new AsyncFunction() {
        @Override public ListenableFuture apply(Object input) {
          return toGuava(new Flush.Builder().addIndex(indices).build());
        }
      });
    }

    return transform(future, Functions.<Void>constant(null));
  }

  @Override protected void ensureClusterReady(final String catchAll) {
    class IndicesHealth extends Health {
      private IndicesHealth(Builder builder) {
        super(builder);
      }

      @Override
      protected String buildURI() {
        return super.buildURI() + catchAll;
      }
    }

    String status = executeUnchecked(new IndicesHealth(new Health.Builder()))
        .getJsonObject().get("status").getAsString();
    checkState(!"RED".equalsIgnoreCase(status), "Health status is RED");
  }

  private Index toIndexRequest(IndexableSpan span) {
    return new Index.Builder(new String(span.data, Charsets.UTF_8))
        .index(span.index)
        .type(SPAN)
        .build();
  }

  @Override
  public void close() {
    client.shutdownClient();
  }

  /**
   * A Search request that matches the behavior of {@link IndicesOptions#lenientExpandOpen()}
   */
  private static final class LenientSearch extends Search {
    LenientSearch(Search.Builder builder) {
      super(builder);
    }

    @Override
    protected String buildURI() {
      return super.buildURI() + "?ignore_unavailable=true&allow_no_indices=true&";
    }
  }

  private <T extends JestResult> ListenableFuture<T> toGuava(Action<T> action) {
    final SettableFuture<T> future = SettableFuture.create();
    class Handler implements JestResultHandler<T> {
      @Override public void completed(T result) {
        future.set(result);
      }

      @Override public void failed(Exception ex) {
        future.setException(ex);
      }
    }
    client.executeAsync(action, new Handler());
    return future;
  }

  private <T extends JestResult> T executeUnchecked(Action<T> action) {
    return getUnchecked(toGuava(action));
  }

  private enum SpanDeserializer implements JsonDeserializer<Span> {
    INSTANCE;

    @Override
    public Span deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      return Codec.JSON.readSpan(json.toString().getBytes(Charsets.UTF_8));
    }
  }

  private enum DependencyLinkDeserializer implements JsonDeserializer<DependencyLink> {
    INSTANCE;

    @Override
    public DependencyLink deserialize(JsonElement json, Type typeOfT,
        JsonDeserializationContext context)
        throws JsonParseException {
      return Codec.JSON.readDependencyLink(json.toString().getBytes(Charsets.UTF_8));
    }
  }

  private static class RestBuckets implements Buckets {
    private final SearchResult response;

    private RestBuckets(SearchResult response) {
      this.response = response;
    }

    @Override
    public List<String> getBucketKeys(String name, String... nestedPath) {
      String[] path = ObjectArrays.concat(name, nestedPath);
      MetricAggregation aggregation = response.getAggregations();

      for (int i = 0; i < path.length - 1 && aggregation != null; i++) {
        aggregation = aggregation.getAggregation(path[i], RootAggregation.class);
      }
      if (aggregation == null) return ImmutableList.of();

      TermsAggregation t = aggregation.getTermsAggregation(path[path.length - 1]);
      if (t == null) return ImmutableList.of();
      return Lists.transform(t.getBuckets(), new Function<TermsAggregation.Entry, String>() {
        @Override
        public String apply(TermsAggregation.Entry input) {
          return input.getKey();
        }
      });
    }
  }
}
