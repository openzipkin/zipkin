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
package zipkin.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesRequest;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Order;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.MergeById;
import zipkin.internal.Nullable;
import zipkin.internal.Util;
import zipkin.spanstore.guava.GuavaSpanStore;
import zipkin.spanstore.guava.GuavaToAsyncSpanStoreAdapter;

import static com.google.common.util.concurrent.Futures.transform;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static zipkin.elasticsearch.ElasticFutures.toGuava;

public class ElasticsearchSpanStore extends GuavaToAsyncSpanStoreAdapter
    implements GuavaSpanStore, AutoCloseable {
  static final long ONE_DAY_IN_MILLIS = TimeUnit.DAYS.toMillis(1);

  private final Client client;
  private final IndexNameFormatter indexNameFormatter;
  private final ElasticsearchSpanConsumer spanConsumer;
  private final String indexTemplate;

  public ElasticsearchSpanStore(ElasticsearchConfig config) {
    this.client = createClient(config.hosts, config.clusterName);
    this.indexNameFormatter = new IndexNameFormatter(config.index);
    this.spanConsumer = new ElasticsearchSpanConsumer(client, indexNameFormatter);
    this.indexTemplate = config.indexTemplate;

    checkForIndexTemplate();
  }

  @Override protected GuavaSpanStore delegate() {
    return this;
  }

  @Override public ListenableFuture<Void> accept(List<Span> spans) {
    return spanConsumer.accept(spans);
  }

  @Override public ListenableFuture<List<List<Span>>> getTraces(QueryRequest request) {
    long endMillis = request.endTs;
    long beginMillis = endMillis - request.lookback;

    String serviceName = request.serviceName.toLowerCase();

    BoolQueryBuilder filter = boolQuery()
        .must(boolQuery()
            .should(termQuery("annotations.endpoint.serviceName", serviceName))
            .should(nestedQuery(
                "binaryAnnotations",
                termQuery("binaryAnnotations.endpoint.serviceName", serviceName))))
        .must(rangeQuery("timestamp")
            .gte(TimeUnit.MILLISECONDS.toMicros(beginMillis))
            .lte(TimeUnit.MILLISECONDS.toMicros(endMillis)));
    if (request.spanName != null) {
      filter.must(termQuery("name", request.spanName));
    }
    for (String annotation : request.annotations) {
      filter.must(termQuery("annotations.value", annotation));
    }
    for (Map.Entry<String, String> annotation : request.binaryAnnotations.entrySet()) {
      // In our index template, we make sure the binaryAnnotation value is indexed as string,
      // meaning non-string values won't even be indexed at all. This means that we can only
      // match string values here, which happens to be exactly what we want.
      filter.must(nestedQuery("binaryAnnotations",
          boolQuery()
              .must(termQuery("binaryAnnotations.key", annotation.getKey()))
              .must(termQuery("binaryAnnotations.value",
                  annotation.getValue()))));
    }

    if (request.minDuration != null) {
      RangeQueryBuilder durationQuery = rangeQuery("duration").gte(request.minDuration);
      if (request.maxDuration != null) {
        durationQuery.lte(request.maxDuration);
      }
      filter.must(durationQuery);
    }

    List<String> strings = computeIndices(beginMillis, endMillis);
    final String[] indices = strings.toArray(new String[strings.size()]);
    // We need to filter to traces that contain at least one span that matches the request,
    // but the zipkin API is supposed to order traces by first span, regardless of if it was
    // filtered or not. This is not possible without either multiple, heavyweight queries
    // or complex multiple indexing, defeating much of the elegance of using elasticsearch for this.
    // So we fudge and order on the first span among the filtered spans - in practice, there should
    // be no significant difference in user experience since span start times are usually very
    // close to each other in human time.
    SearchRequestBuilder elasticRequest =
        client.prepareSearch(indices)
            .setIndicesOptions(IndicesOptions.lenientExpandOpen())
            .setTypes(ElasticsearchConstants.SPAN)
            .setQuery(boolQuery().must(matchAllQuery()).filter(filter))
            .setSize(0)
            .addAggregation(
                AggregationBuilders.terms("traceId_agg")
                    .field("traceId")
                    .subAggregation(AggregationBuilders.min("timestamps_agg").field("timestamp"))
                    .order(Order.aggregation("timestamps_agg", false))
                    .size(request.limit));

    ListenableFuture<SearchResponse> traceIds = toGuava(elasticRequest.execute());

    return transform(traceIds, new AsyncFunction<SearchResponse, List<List<Span>>>() {
          @Override public ListenableFuture<List<List<Span>>> apply(SearchResponse input) {
            if (input.getAggregations() == null
                || input.getAggregations().get("traceId_agg") == null) {
              return Futures.immediateFuture(Collections.<List<Span>>emptyList());
            }
            Terms traceIdsAgg = input.getAggregations().get("traceId_agg");
            List<Long> traceIds = new ArrayList<>();
            for (Terms.Bucket bucket : traceIdsAgg.getBuckets()) {
              traceIds.add(Util.lowerHexToUnsignedLong(bucket.getKeyAsString()));
            }
            return getTracesByIds(traceIds, indices);
          }
        }
    );
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceId) {
    return transform(getRawTrace(traceId), new Function<List<Span>, List<Span>>() {
      @Override public List<Span> apply(List<Span> input) {
        return input == null ? null : CorrectForClockSkew.apply(MergeById.apply(input));
      }
    });
  }

  @Override public ListenableFuture<List<Span>> getRawTrace(long traceId) {
    SearchRequestBuilder elasticRequest = client.prepareSearch(indexNameFormatter.catchAll())
        .setTypes(ElasticsearchConstants.SPAN)
        .setQuery(termQuery("traceId", String.format("%016x", traceId)));

    return transform(toGuava(elasticRequest.execute()), new Function<SearchResponse, List<Span>>() {
      @Override public List<Span> apply(SearchResponse response) {
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

  ListenableFuture<List<List<Span>>> getTracesByIds(Collection<Long> traceIds, String[] indices) {
    List<String> traceIdsStr = new ArrayList<>(traceIds.size());
    for (long traceId : traceIds) {
      traceIdsStr.add(String.format("%016x", traceId));
    }
    SearchRequestBuilder elasticRequest = client.prepareSearch(indices)
        .setIndicesOptions(IndicesOptions.lenientExpandOpen())
        .setTypes(ElasticsearchConstants.SPAN)
        // TODO: This is the default maximum size of an elasticsearch result set.
        // Need to determine whether this is enough by zipkin standards or should
        // increase it in the index template.
        .setSize(10000)
        .setQuery(termsQuery("traceId", traceIdsStr));
    return Futures.transform(toGuava(elasticRequest.execute()), ConvertTracesResponse.INSTANCE);
  }

  @Override public void close() {
    client.close();
  }

  enum ConvertTracesResponse implements Function<SearchResponse, List<List<Span>>> {
    INSTANCE;

    @Override public List<List<Span>> apply(SearchResponse response) {

      ArrayListMultimap<Long, Span> groupedSpans = ArrayListMultimap.create();
      for (SearchHit hit : response.getHits()) {
        Span span = Codec.JSON.readSpan(hit.getSourceRef().toBytes());
        groupedSpans.put(span.traceId, span);
      }
      List<List<Span>> result = new ArrayList<>(groupedSpans.size());
      for (Long traceId : groupedSpans.keySet()) {
        result.add(CorrectForClockSkew.apply(MergeById.apply(groupedSpans.get(traceId))));
      }
      return TRACE_DESCENDING.immutableSortedCopy(result);
    }
  }

  @Override public ListenableFuture<List<String>> getServiceNames() {
    SearchRequestBuilder elasticRequest =
        client.prepareSearch(indexNameFormatter.catchAll())
            .setTypes(ElasticsearchConstants.SPAN)
            .setQuery(matchAllQuery())
            .setSize(0)
            .addAggregation(AggregationBuilders.terms("annotationServiceName_agg")
                .field("annotations.endpoint.serviceName")
                .size(0))
            .addAggregation(AggregationBuilders.nested("binaryAnnotations_agg")
                .path("binaryAnnotations")
                .subAggregation(AggregationBuilders.terms("binaryAnnotationsServiceName_agg")
                    .field("binaryAnnotations.endpoint.serviceName")
                    .size(0)));

    return transform(toGuava(elasticRequest.execute()), ConvertServiceNamesResponse.INSTANCE);
  }

  enum ConvertServiceNamesResponse implements Function<SearchResponse, List<String>> {
    INSTANCE;

    @Override public List<String> apply(SearchResponse response) {
      if (response.getAggregations() == null) {
        return Collections.emptyList();
      }
      SortedSet<String> serviceNames = new TreeSet<>();
      Terms annotationServiceNamesAgg = response.getAggregations().get("annotationServiceName_agg");
      if (annotationServiceNamesAgg != null) {
        for (Terms.Bucket bucket : annotationServiceNamesAgg.getBuckets()) {
          if (!bucket.getKeyAsString().isEmpty()) {
            serviceNames.add(bucket.getKeyAsString());
          }
        }
      }
      Nested binaryAnnotationsAgg = response.getAggregations().get("binaryAnnotations_agg");
      if (binaryAnnotationsAgg != null && binaryAnnotationsAgg.getAggregations() != null) {
        Terms binaryAnnotationServiceNamesAgg = binaryAnnotationsAgg.getAggregations()
            .get("binaryAnnotationsServiceName_agg");
        if (binaryAnnotationServiceNamesAgg != null) {
          for (Terms.Bucket bucket : binaryAnnotationServiceNamesAgg.getBuckets()) {
            if (!bucket.getKeyAsString().isEmpty()) {
              serviceNames.add(bucket.getKeyAsString());
            }
          }
        }
      }
      return ImmutableList.copyOf(serviceNames);
    }
  }

  @Override public ListenableFuture<List<String>> getSpanNames(String serviceName) {
    if (Strings.isNullOrEmpty(serviceName)) {
      return EMPTY_LIST;
    }
    serviceName = serviceName.toLowerCase();
    QueryBuilder filter = boolQuery()
        .should(termQuery("annotations.endpoint.serviceName", serviceName))
        .should(termQuery("binaryAnnotations.endpoint.serviceName", serviceName));
    SearchRequestBuilder elasticRequest = client.prepareSearch(indexNameFormatter.catchAll())
        .setTypes(ElasticsearchConstants.SPAN)
        .setQuery(boolQuery().must(matchAllQuery()).filter(filter))
        .setSize(0)
        .addAggregation(AggregationBuilders.terms("name_agg")
            .order(Order.term(true))
            .field("name")
            .size(0));

    return transform(toGuava(elasticRequest.execute()), ConvertSpanNameResponse.INSTANCE);
  }

  enum ConvertSpanNameResponse implements Function<SearchResponse, List<String>> {
    INSTANCE;

    @Override public List<String> apply(SearchResponse response) {
      Terms namesAgg = response.getAggregations().get("name_agg");
      if (namesAgg == null) {
        return Collections.emptyList();
      }
      ImmutableList.Builder<String> spanNames = ImmutableList.builder();
      for (Terms.Bucket bucket : namesAgg.getBuckets()) {
        spanNames.add(bucket.getKeyAsString());
      }
      return spanNames.build();
    }
  }

  @Override public ListenableFuture<List<DependencyLink>> getDependencies(long endMillis,
      @Nullable Long lookback) {
    long beginMillis = lookback != null ? endMillis - lookback : 0;
    // We just return all dependencies in the days that fall within endTs and lookback as
    // dependency links themselves don't have timestamps.
    List<String> strings = computeIndices(beginMillis, endMillis);
    SearchRequestBuilder elasticRequest = client.prepareSearch(
        strings.toArray(new String[strings.size()]))
        .setIndicesOptions(IndicesOptions.lenientExpandOpen())
        .setTypes(ElasticsearchConstants.DEPENDENCY_LINK)
        .addAggregation(AggregationBuilders.terms("parent_child_agg")
            .field("parent_child")
            .subAggregation(AggregationBuilders.topHits("hits_agg")
                .setSize(1))
            .subAggregation(AggregationBuilders.sum("callCount_agg")
                .field("callCount")))
        .setQuery(matchAllQuery());

    return transform(toGuava(elasticRequest.execute()), ConvertDependenciesResponse.INSTANCE);
  }

  enum ConvertDependenciesResponse implements Function<SearchResponse, List<DependencyLink>> {
    INSTANCE;

    @Override public List<DependencyLink> apply(SearchResponse response) {
      if (response.getAggregations() == null) {
        return Collections.emptyList();
      }
      Terms parentChildAgg = response.getAggregations().get("parent_child_agg");
      if (parentChildAgg == null) {
        return Collections.emptyList();
      }
      ImmutableList.Builder<DependencyLink> links = ImmutableList.builder();
      for (Terms.Bucket bucket : parentChildAgg.getBuckets()) {
        TopHits hitsAgg = bucket.getAggregations().get("hits_agg");
        Sum callCountAgg = bucket.getAggregations().get("callCount_agg");
        // We would have no bucket if there wasn't a hit, so this should always be non-empty.
        SearchHit hit = hitsAgg.getHits().getAt(0);
        DependencyLink link = Codec.JSON.readDependencyLink(hit.getSourceRef().toBytes());
        link = new DependencyLink.Builder(link).callCount((long) callCountAgg.getValue()).build();
        links.add(link);
      }
      return links.build();
    }
  }

  @VisibleForTesting void clear() {
    client.admin().indices().delete(new DeleteIndexRequest(indexNameFormatter.catchAll()))
        .actionGet();
    client.admin().indices().flush(new FlushRequest()).actionGet();
  }

  @VisibleForTesting void writeDependencyLinks(List<DependencyLink> links, long timestampMillis) {
    timestampMillis = Util.midnightUTC(timestampMillis);
    BulkRequestBuilder request = client.prepareBulk();
    for (DependencyLink link : links) {
      request.add(client.prepareIndex(
          indexNameFormatter.indexNameForTimestamp(timestampMillis),
          ElasticsearchConstants.DEPENDENCY_LINK)
          .setSource(
              "parent", link.parent,
              "child", link.child,
              "parent_child", link.parent + "|" + link.child,  // For aggregating callCount
              "callCount", link.callCount));
    }
    request.execute().actionGet();
    client.admin().indices().flush(new FlushRequest()).actionGet();
  }

  private List<String> computeIndices(long beginMillis, long endMillis) {
    beginMillis = Util.midnightUTC(beginMillis);
    endMillis = Util.midnightUTC(endMillis);

    List<String> indices = new ArrayList<>();
    // If a leap second is involved, the same index will be specified twice.
    // It shouldn't be a big deal.
    for (long currentMillis = beginMillis; currentMillis <= endMillis;
        currentMillis += ONE_DAY_IN_MILLIS) {
      indices.add(indexNameFormatter.indexNameForTimestamp(currentMillis));
    }
    return indices;
  }

  private void checkForIndexTemplate() {
    GetIndexTemplatesResponse existingTemplates =
        client.admin().indices().getTemplates(new GetIndexTemplatesRequest("zipkin_template"))
            .actionGet();
    if (!existingTemplates.getIndexTemplates().isEmpty()) {
      return;
    }
    client.admin().indices().putTemplate(
        new PutIndexTemplateRequest("zipkin_template").source(indexTemplate)).actionGet();
  }

  private static Client createClient(List<String> hosts, String clusterName) {
    Settings settings = Settings.builder()
        .put("cluster.name", clusterName)
        .put("client.transport.sniff", true)
        .build();

    TransportClient client = TransportClient.builder()
        .settings(settings)
        .build();
    for (String host : hosts) {
      HostAndPort hostAndPort = HostAndPort.fromString(host);
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
    return client;
  }
}
