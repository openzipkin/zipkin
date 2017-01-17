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
package zipkin.storage.elasticsearch;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionResponse;
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
import org.elasticsearch.common.io.FastStringReader;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Lazy;
import zipkin.internal.Util;
import zipkin.storage.Callback;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.Futures.transform;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

/**
 * An implementation of {@link InternalElasticsearchClient} that wraps a {@link
 * org.elasticsearch.client.Client}
 */
final class NativeClient extends InternalElasticsearchClient {

  static final class Builder extends InternalElasticsearchClient.Builder {
    String cluster = "elasticsearch";
    Lazy<List<String>> hosts;
    boolean flushOnWrites;
    String clientVersion;

    Builder() {
      hosts(Collections.singletonList("localhost:9300"));
    }

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
    @Override public Builder hosts(Lazy<List<String>> hosts) {
      this.hosts = checkNotNull(hosts, "hosts");
      return this;
    }

    @Override public Builder flushOnWrites(boolean flushOnWrites) {
      this.flushOnWrites = flushOnWrites;
      return this;
    }

    @Override public Factory buildFactory() {
      try {
        String properties =
            Resources.toString(Resources.getResource("es-build.properties"), UTF_8);
        Properties props = new Properties();
        props.load(new FastStringReader(properties));
        clientVersion = props.getProperty("version", "2.x");
      } catch (IOException ignored) {
      }
      return new Factory(this);
    }
  }

  private static final class Factory implements InternalElasticsearchClient.Factory {
    final String cluster;
    final String clientVersion;
    final Lazy<List<String>> hosts;
    final boolean flushOnWrites;

    Factory(Builder builder) {
      this.cluster = builder.cluster;
      this.clientVersion = builder.clientVersion;
      this.hosts = builder.hosts;
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
      for (String host : hosts.get()) {
        HostAndPort hostAndPort = HostAndPort.fromString(host).withDefaultPort(9300);
        try {
          client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(
              hostAndPort.getHostText()), hostAndPort.getPort()));
        } catch (UnknownHostException e) {
          // Hosts may be down transiently, we should still try to connect. If all of them happen
          // to be down we will fail later when trying to use the client when checking the index
          // template.
        }
      }
      return new NativeClient(client, clientVersion, flushOnWrites);
    }

    @Override public String toString() {
      StringBuilder json = new StringBuilder("{\"clusterName\": \"").append(cluster).append("\"");
      json.append(", \"hosts\": [\"").append(Joiner.on("\", \"").join(hosts.get())).append("\"]");
      return json.append("}").toString();
    }
  }

  final TransportClient client;
  final String clientVersion;
  final boolean flushOnWrites;

  NativeClient(TransportClient client, String clientVersion, boolean flushOnWrites) {
    this.client = client;
    this.clientVersion = clientVersion;
    this.flushOnWrites = flushOnWrites;
  }

  /** Since the wire protocol doesn't support version negotiation, return the library version */
  @Override protected String getVersion() {
    return clientVersion;
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
  public ListenableFuture<List<String>> collectBucketKeys(String[] indices, QueryBuilder query,
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

    return transform(toGuava(elasticRequest.execute()), BucketKeys.INSTANCE);
  }

  enum BucketKeys implements Function<SearchResponse, List<String>> {
    INSTANCE;

    @Override public List<String> apply(SearchResponse input) {
      Iterator<Aggregation> aggregations = input.getAggregations() != null
          ? input.getAggregations().iterator()
          : null;
      if (aggregations == null) {
        return ImmutableList.of();
      }
      ImmutableSet.Builder<String> result = ImmutableSet.builder();
      while (aggregations.hasNext()) {
        addBucketKeys(aggregations.next(), result);
      }
      return Util.sortedList(result.build());
    }

    static void addBucketKeys(Aggregation input, ImmutableSet.Builder<String> result) {
      if (input instanceof MultiBucketsAggregation) {
        MultiBucketsAggregation aggregation = (MultiBucketsAggregation) input;
        for (MultiBucketsAggregation.Bucket bucket : aggregation.getBuckets()) {
          result.add(bucket.getKeyAsString());
        }
      } else if (input instanceof SingleBucketAggregation) {
        SingleBucketAggregation aggregation = (SingleBucketAggregation) input;
        for (Aggregation next : aggregation.getAggregations()) {
          addBucketKeys(next, result);
        }
      }
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
  public ListenableFuture<List<DependencyLink>> findDependencies(String[] indices) {
    SearchRequestBuilder elasticRequest = client.prepareSearch(
        indices)
        .setIndicesOptions(IndicesOptions.lenientExpandOpen())
        .setTypes(DEPENDENCY_LINK)
        .setQuery(matchAllQuery());

    return transform(toGuava(elasticRequest.execute()), ConvertDependenciesResponse.INSTANCE);
  }

  enum ConvertDependenciesResponse implements Function<SearchResponse, List<DependencyLink>> {
    INSTANCE;

    @Override public List<DependencyLink> apply(SearchResponse response) {
      if (response.getHits() == null) return ImmutableList.of();

      ImmutableList.Builder<DependencyLink> unmerged = ImmutableList.builder();
      for (SearchHit hit : response.getHits()) {
        DependencyLink link = Codec.JSON.readDependencyLink(hit.getSourceRef().toBytes());
        unmerged.add(link);
      }

      return unmerged.build();
    }
  }

  @Override protected BulkSpanIndexer bulkSpanIndexer() {
    return new SpanBytesBulkSpanIndexer() {
      final List<IndexRequestBuilder> indexRequests = new LinkedList<>();
      final Set<String> indicesToFlush = new LinkedHashSet<>();

      @Override protected void add(String index, byte[] spanBytes) {
        indexRequests.add(client.prepareIndex(index, SPAN).setSource(spanBytes));
        if (flushOnWrites) indicesToFlush.add(index);
      }

      // Creates a bulk request when there is more than one span to store
      @Override public void execute(final Callback<Void> callback) {
        ActionListener callbackAdapter = new ActionListener() {
          @Override public void onResponse(Object input) {
            callback.onSuccess(null);
          }

          @Override public void onFailure(Throwable throwable) {
            callback.onError(throwable);
          }
        };

        // Conditionally create a bulk action depending on the count of index requests
        ListenableActionFuture<? extends ActionResponse> future;
        if (indexRequests.size() == 1) {
          future = indexRequests.get(0).execute();
        } else {
          BulkRequestBuilder request = client.prepareBulk();
          for (IndexRequestBuilder span : indexRequests) {
            request.add(span);
          }
          future = request.execute();
        }

        // Unless we are in a unit test, this should always be true
        if (indicesToFlush.isEmpty()) {
          future.addListener(callbackAdapter);
          return;
        }

        // If we are in a unit test, we need to flush so that we can read our writes
        future.addListener(new ActionListener() {
          @Override public void onResponse(Object input) {
            client.admin().indices()
                .prepareFlush(indicesToFlush.toArray(new String[indicesToFlush.size()]))
                .execute().addListener(callbackAdapter);
          }

          @Override public void onFailure(Throwable throwable) {
            callbackAdapter.onFailure(throwable);
          }
        });
      }
    };
  }

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
}
