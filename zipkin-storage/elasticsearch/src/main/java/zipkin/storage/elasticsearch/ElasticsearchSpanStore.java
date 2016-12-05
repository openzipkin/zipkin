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
import com.google.common.collect.FluentIterable;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Order;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.DependencyLinker;
import zipkin.internal.GroupByTraceId;
import zipkin.internal.MergeById;
import zipkin.internal.Nullable;
import zipkin.internal.Util;
import zipkin.storage.QueryRequest;
import zipkin.storage.guava.GuavaSpanStore;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

final class ElasticsearchSpanStore implements GuavaSpanStore {
  static final ListenableFuture<List<String>> EMPTY_LIST =
      immediateFuture(Collections.<String>emptyList());

  private final InternalElasticsearchClient client;
  private final IndexNameFormatter indexNameFormatter;
  private final String[] catchAll;
  private final boolean strictTraceId;

  ElasticsearchSpanStore(InternalElasticsearchClient client, IndexNameFormatter indexNameFormatter,
      boolean strictTraceId) {
    this.client = client;
    this.indexNameFormatter = indexNameFormatter;
    this.catchAll = new String[] {indexNameFormatter.catchAll()};
    this.strictTraceId = strictTraceId;
  }

  @Override public ListenableFuture<List<List<Span>>> getTraces(final QueryRequest request) {
    long endMillis = request.endTs;
    long beginMillis = endMillis - request.lookback;

    BoolQueryBuilder filter = boolQuery()
        .must(rangeQuery("timestamp_millis")
            .gte(beginMillis)
            .lte(endMillis));

    if (request.serviceName != null) {
      filter.must(boolQuery()
          .should(nestedQuery(
              "annotations", termQuery("annotations.endpoint.serviceName", request.serviceName)))
          .should(nestedQuery(
              "binaryAnnotations",
              termQuery("binaryAnnotations.endpoint.serviceName", request.serviceName))));
    }
    if (request.spanName != null) {
      filter.must(termQuery("name", request.spanName));
    }
    for (String annotation : request.annotations) {
      BoolQueryBuilder annotationQuery = boolQuery()
          .must(termQuery("annotations.value", annotation));

      if (request.serviceName != null) {
        annotationQuery.must(termQuery("annotations.endpoint.serviceName", request.serviceName));
      }

      filter.must(nestedQuery("annotations", annotationQuery));
    }
    for (Map.Entry<String, String> kv : request.binaryAnnotations.entrySet()) {
      // In our index template, we make sure the binaryAnnotation value is indexed as string,
      // meaning non-string values won't even be indexed at all. This means that we can only
      // match string values here, which happens to be exactly what we want.
      BoolQueryBuilder binaryAnnotationQuery = boolQuery()
          .must(termQuery("binaryAnnotations.key", kv.getKey()))
          .must(termQuery("binaryAnnotations.value", kv.getValue()));

      if (request.serviceName != null) {
        binaryAnnotationQuery.must(
            termQuery("binaryAnnotations.endpoint.serviceName", request.serviceName));
      }

      filter.must(nestedQuery("binaryAnnotations", binaryAnnotationQuery));
    }

    if (request.minDuration != null) {
      RangeQueryBuilder durationQuery = rangeQuery("duration").gte(request.minDuration);
      if (request.maxDuration != null) {
        durationQuery.lte(request.maxDuration);
      }
      filter.must(durationQuery);
    }

    Set<String> strings = indexNameFormatter.indexNamePatternsForRange(beginMillis, endMillis);
    final String[] indices = strings.toArray(new String[0]);
    // We need to filter to traces that contain at least one span that matches the request,
    // but the zipkin API is supposed to order traces by first span, regardless of if it was
    // filtered or not. This is not possible without either multiple, heavyweight queries
    // or complex multiple indexing, defeating much of the elegance of using elasticsearch for this.
    // So we fudge and order on the first span among the filtered spans - in practice, there should
    // be no significant difference in user experience since span start times are usually very
    // close to each other in human time.
    ListenableFuture<List<String>> traceIds =
        client.collectBucketKeys(indices,
            boolQuery().must(matchAllQuery()).filter(filter),
            AggregationBuilders.terms("traceId_agg")
                .field("traceId")
                .subAggregation(AggregationBuilders.min("timestamps_agg")
                    .field("timestamp_millis"))
                .order(Order.aggregation("timestamps_agg", false))
                .size(request.limit));

    return transform(traceIds, new AsyncFunction<List<String>, List<List<Span>>>() {
          @Override public ListenableFuture<List<List<Span>>> apply(List<String> input) {
            return getTracesByIds(input, indices, request);
          }
        }
    );
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceId) {
    return getTrace(0L, traceId);
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceIdHigh, long traceIdLow) {
    return transform(getRawTrace(traceIdHigh, traceIdLow), AdjustTrace.INSTANCE);
  }

  enum AdjustTrace implements Function<Collection<Span>, List<Span>> {
    INSTANCE;

    @Override public List<Span> apply(Collection<Span> input) {
      List<Span> result = CorrectForClockSkew.apply(MergeById.apply(input));
      return result.isEmpty() ? null : result;
    }
  }

  @Override public ListenableFuture<List<Span>> getRawTrace(long traceId) {
    return getRawTrace(0L, traceId);
  }

  @Override public ListenableFuture<List<Span>> getRawTrace(long traceIdHigh, long traceIdLow) {
    String traceIdHex = Util.toLowerHex(strictTraceId ? traceIdHigh : 0L, traceIdLow);
    return client.findSpans(catchAll, termQuery("traceId", traceIdHex));
  }

  ListenableFuture<List<List<Span>>> getTracesByIds(Collection<String> traceIds, String[] indices,
      final QueryRequest request) {
    return Futures.transform(client.findSpans(indices, termsQuery("traceId", traceIds)),
        new Function<List<Span>, List<List<Span>>>() {
          @Override public List<List<Span>> apply(List<Span> input) {
            if (input == null) return Collections.emptyList();
            // Due to tokenization of the trace ID, our matches are imprecise on Span.traceIdHigh
            return FluentIterable.from(GroupByTraceId.apply(input, strictTraceId, true))
                .filter(trace -> trace.get(0).traceIdHigh == 0 || request.test(trace)).toList();
          }
        });
  }

  @Override public ListenableFuture<List<String>> getServiceNames() {
    return client.collectBucketKeys(catchAll, matchAllQuery(),
        AggregationBuilders.nested("annotations_agg")
            .path("annotations")
            .subAggregation(AggregationBuilders.terms("annotationsServiceName_agg")
                .field("annotations.endpoint.serviceName")
                .size(Integer.MAX_VALUE)),
        AggregationBuilders.nested("binaryAnnotations_agg")
            .path("binaryAnnotations")
            .subAggregation(AggregationBuilders.terms("binaryAnnotationsServiceName_agg")
                .field("binaryAnnotations.endpoint.serviceName")
                .size(Integer.MAX_VALUE)));
  }

  @Override public ListenableFuture<List<String>> getSpanNames(String serviceName) {
    if (Strings.isNullOrEmpty(serviceName)) {
      return EMPTY_LIST;
    }
    serviceName = serviceName.toLowerCase();

    QueryBuilder filter = boolQuery()
        .should(nestedQuery(
            "annotations", termQuery("annotations.endpoint.serviceName", serviceName)))
        .should(nestedQuery(
            "binaryAnnotations", termQuery("binaryAnnotations.endpoint.serviceName", serviceName)));

    return client.collectBucketKeys(catchAll,
        boolQuery().must(matchAllQuery()).filter(filter),
        AggregationBuilders.terms("name_agg")
            .order(Order.term(true))
            .field("name")
            .size(Integer.MAX_VALUE));
  }

  @Override public ListenableFuture<List<DependencyLink>> getDependencies(long endMillis,
      @Nullable Long lookback) {
    long beginMillis = lookback != null ? endMillis - lookback : 0;
    // We just return all dependencies in the days that fall within endTs and lookback as
    // dependency links themselves don't have timestamps.
    Set<String> indices = indexNameFormatter.indexNamePatternsForRange(beginMillis, endMillis);
    return Futures.transform(client.findDependencies(indices.toArray(new String[0])),
        new Function<List<DependencyLink>, List<DependencyLink>>() {
          @Override
          public List<DependencyLink> apply(List<DependencyLink> input) {
            return input == null
                ? Collections.<DependencyLink>emptyList()
                : DependencyLinker.merge(input);
          }
        });
  }
}
