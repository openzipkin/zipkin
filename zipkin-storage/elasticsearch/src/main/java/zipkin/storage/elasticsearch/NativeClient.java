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

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesRequest;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Span;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.Futures.transform;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

/**
 * An implementation of {@link InternalElasticsearchClient} that wraps a {@link
 * org.elasticsearch.client.Client}
 */
final class NativeClient extends InternalElasticsearchClient {

  static final class Builder implements InternalElasticsearchClient.Builder {
    String cluster = "elasticsearch";
    List<String> hosts = Collections.singletonList("localhost:9300");
    boolean flushOnWrites;

    /**
     * The elasticsearch cluster to connect to, defaults to "elasticsearch".
     */
    @Override public Builder cluster(String cluster) {
      this.cluster = checkNotNull(cluster, "cluster");
      return this;
    }

    /**
     * A comma separated list of elasticsearch hostnodes to connect to, in host:port format. The
     * port should be the transport port, not the http port. Defaults to "localhost:9300".
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

    Builder() {
    }
  }

  private static final class Factory implements InternalElasticsearchClient.Factory {
    final String cluster;
    final List<String> hosts;
    final boolean flushOnWrites;

    Factory(Builder builder) {
      this.cluster = builder.cluster;
      this.hosts = ImmutableList.copyOf(builder.hosts);
      this.flushOnWrites = builder.flushOnWrites;
    }

    @Override public InternalElasticsearchClient create(String allIndices) {
      Settings settings = Settings.builder()
          .put("cluster.name", cluster)
          .put("lazyClient.transport.sniff", true)
          .build();

      TransportClient client = TransportClient.builder()
          .settings(settings)
          .build();
      for (String host : hosts) {
        HostAndPort hostAndPort = HostAndPort.fromString(host).withDefaultPort(9300);
        try {
          client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(
              hostAndPort.getHostText()), hostAndPort.getPort()));
        } catch (UnknownHostException e) {
          // Hosts may be down transiently, we should still try to connect. If all of them happen
          // to be down we will fail later when trying to use the client when checking the index
          // template.
          continue;
        }
      }
      return new NativeClient(client, flushOnWrites);
    }

    @Override public String toString() {
      StringBuilder json = new StringBuilder("{\"clusterName\": \"").append(cluster).append("\"");
      json.append(", \"hosts\": [\"").append(Joiner.on("\", \"").join(hosts)).append("\"]");
      return json.append("}").toString();
    }
  }

  final TransportClient client;
  final boolean flushOnWrites;

  NativeClient(TransportClient client, boolean flushOnWrites) {
    this.client = client;
    this.flushOnWrites = flushOnWrites;
  }

  @Override
  public void ensureTemplate(String name, String indexTemplate) {
    GetIndexTemplatesResponse existingTemplates =
        client.admin().indices().getTemplates(new GetIndexTemplatesRequest(name))
            .actionGet();
    if (!existingTemplates.getIndexTemplates().isEmpty()) {
      return;
    }
    client.admin().indices().putTemplate(
        new PutIndexTemplateRequest(name).source(indexTemplate)).actionGet();
  }

  @Override
  public void clear(String index) {
    client.admin().indices().delete(new DeleteIndexRequest(index)).actionGet();
    client.admin().indices().flush(new FlushRequest(index)).actionGet();
  }

  @Override
  public ListenableFuture<Buckets> scanTraces(String[] indices, QueryBuilder query,
      AbstractAggregationBuilder... aggregations) {
    SearchRequestBuilder elasticRequest =
        client.prepareSearch(indices)
            .setIndicesOptions(IndicesOptions.lenientExpandOpen())
            .setTypes(SPAN)
            .setQuery(query)
            .setSize(0);

    for (AbstractAggregationBuilder aggregation : aggregations) {
      elasticRequest.addAggregation(aggregation);
    }

    return transform(toGuava(elasticRequest.execute()), AsNativeBuckets.INSTANCE);
  }

  private enum AsNativeBuckets implements Function<SearchResponse, Buckets> {
    INSTANCE;

    @Override public Buckets apply(SearchResponse input) {
      return new NativeBuckets(input);
    }
  }

  @Override
  public ListenableFuture<List<Span>> findSpans(String[] indices, QueryBuilder query) {
    SearchRequestBuilder elasticRequest = client.prepareSearch(indices)
        .setIndicesOptions(IndicesOptions.lenientExpandOpen())
        .setTypes(SPAN)
        .setSize(MAX_RAW_SPANS)
        .setQuery(query);

    return transform(toGuava(elasticRequest.execute()),
        new Function<SearchResponse, List<Span>>() {
          @Override
          public List<Span> apply(SearchResponse response) {
            if (response.getHits().totalHits() == 0) {
              return null;
            }
            ImmutableList.Builder<Span> trace = ImmutableList.builder();
            for (SearchHit hit : response.getHits()) {
              trace.add(Codec.JSON.readSpan(hit.getSourceRef().toBytes()));
            }
            return trace.build();
          }
        });
  }

  @Override
  public ListenableFuture<Collection<DependencyLink>> findDependencies(String[] indices) {
    SearchRequestBuilder elasticRequest = client.prepareSearch(
        indices)
        .setIndicesOptions(IndicesOptions.lenientExpandOpen())
        .setTypes(DEPENDENCY_LINK)
        .setQuery(matchAllQuery());

    return transform(toGuava(elasticRequest.execute()), ConvertDependenciesResponse.INSTANCE);
  }

  enum ConvertDependenciesResponse implements Function<SearchResponse, Collection<DependencyLink>> {
    INSTANCE;

    @Override public Collection<DependencyLink> apply(SearchResponse response) {
      if (response.getHits() == null) return ImmutableList.of();

      ImmutableList.Builder<DependencyLink> unmerged = ImmutableList.builder();
      for (SearchHit hit : response.getHits()) {
        DependencyLink link = Codec.JSON.readDependencyLink(hit.getSourceRef().toBytes());
        unmerged.add(link);
      }

      return unmerged.build();
    }
  }

  @Override
  public ListenableFuture<Void> indexSpans(List<IndexableSpan> spans) {
    if (spans.isEmpty()) return Futures.immediateFuture(null);

    // Create a bulk request when there is more than one span to store
    ListenableFuture<?> future;
    final Set<String> indices = new HashSet<>();
    if (spans.size() == 1) {
      IndexableSpan span = getOnlyElement(spans);
      future = toGuava(toIndexRequest(span).execute());
      if (flushOnWrites) indices.add(span.index);
    } else {
      BulkRequestBuilder request = client.prepareBulk();
      for (IndexableSpan span : spans) {
        request.add(toIndexRequest(span));
        if (flushOnWrites) indices.add(span.index);
      }
      future = toGuava(request.execute());
    }

    if (flushOnWrites) {
      future = transform(future, new AsyncFunction() {
        @Override public ListenableFuture apply(Object input) {
          return toGuava(client.admin().indices()
              .prepareFlush(indices.toArray(new String[indices.size()]))
              .execute());
        }
      });
    }

    return transform(future, TO_VOID);
  }

  private IndexRequestBuilder toIndexRequest(IndexableSpan span) {
    return client.prepareIndex(span.index, SPAN).setSource(span.data);
  }

  private static final Function<Object, Void> TO_VOID = Functions.constant(null);

  @Override protected void ensureClusterReady(String catchAll) {
    ClusterHealthResponse health = getUnchecked(client
        .admin().cluster().prepareHealth(catchAll).execute());

    checkState(health.getStatus() != ClusterHealthStatus.RED, "Health status is RED");
  }

  @Override public void close() {
    client.close();
  }

  static <T> ListenableFuture<T> toGuava(ListenableActionFuture<T> elasticFuture) {
    final SettableFuture<T> future = SettableFuture.create();
    elasticFuture.addListener(new ActionListener<T>() {
      @Override public void onResponse(T t) {
        future.set(t);
      }

      @Override public void onFailure(Throwable e) {
        future.setException(e);
      }
    });
    return future;
  }

  static class NativeBuckets implements Buckets {
    private final SearchResponse response;

    NativeBuckets(SearchResponse response) {
      this.response = response;
    }

    @Override
    public List<String> getBucketKeys(String name, String... nestedPath) {
      String[] path = ObjectArrays.concat(name, nestedPath);
      Aggregations aggregations = response.getAggregations();

      for (int i = 0; i < path.length - 1 && aggregations != null; i++) {
        Nested n = aggregations.get(path[i]);

        if (n != null) {
          aggregations = n.getAggregations();
        } else {
          aggregations = null;
        }
      }
      if (aggregations == null) return ImmutableList.of();

      Terms t = aggregations.get(path[path.length - 1]);
      if (t == null) return ImmutableList.of();
      return Lists.transform(t.getBuckets(), new Function<Terms.Bucket, String>() {
        @Override
        public String apply(Terms.Bucket input) {
          return input.getKeyAsString();
        }
      });
    }
  }
}
