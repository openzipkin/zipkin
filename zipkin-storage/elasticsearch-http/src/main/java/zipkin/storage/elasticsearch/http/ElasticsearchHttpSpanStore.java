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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.GroupByTraceId;
import zipkin.internal.MergeById;
import zipkin.internal.Nullable;
import zipkin.internal.Util;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;
import zipkin.storage.elasticsearch.http.internal.client.Aggregation;
import zipkin.storage.elasticsearch.http.internal.client.HttpCall;
import zipkin.storage.elasticsearch.http.internal.client.SearchCallFactory;
import zipkin.storage.elasticsearch.http.internal.client.SearchRequest;

import static java.util.Arrays.asList;

final class ElasticsearchHttpSpanStore implements AsyncSpanStore {

  static final String DEPENDENCY_LINK = "dependencylink";
  static final String SPAN2 = "span2";

  final SearchCallFactory search;
  final String[] allIndices;
  final IndexNameFormatter indexNameFormatter;
  final boolean strictTraceId;
  final int namesLookback;

  ElasticsearchHttpSpanStore(ElasticsearchHttpStorage es) {
    this.search = new SearchCallFactory(es.http());
    this.allIndices = new String[] {es.indexNameFormatter().allIndices()};
    this.indexNameFormatter = es.indexNameFormatter();
    this.strictTraceId = es.strictTraceId();
    this.namesLookback = es.namesLookback();
  }

  @Override public void getTraces(QueryRequest request, Callback<List<List<Span>>> callback) {
    long beginMillis = request.endTs - request.lookback;
    long endMillis = request.endTs;

    SearchRequest.Filters filters = new SearchRequest.Filters();
    filters.addRange("timestamp_millis", beginMillis, endMillis);
    if (request.serviceName != null) {
      filters.addTerm("localEndpoint.serviceName", request.serviceName);
    }

    if (request.spanName != null) {
      filters.addTerm("name", request.spanName);
    }

    for (String annotation : request.annotations) {
      filters.should()
        .addTerm("annotations.value", annotation)
        .addExists("tags." + annotation);
    }

    for (Map.Entry<String, String> kv : request.binaryAnnotations.entrySet()) {
      filters.addTerm("tags." + kv.getKey(), kv.getValue());
    }

    if (request.minDuration != null) {
      filters.addRange("duration", request.minDuration, request.maxDuration);
    }

    // We need to filter to traces that contain at least one span that matches the request,
    // but the zipkin API is supposed to order traces by first span, regardless of if it was
    // filtered or not. This is not possible without either multiple, heavyweight queries
    // or complex multiple indexing, defeating much of the elegance of using elasticsearch for this.
    // So we fudge and order on the first span among the filtered spans - in practice, there should
    // be no significant difference in user experience since span start times are usually very
    // close to each other in human time.
    Aggregation traceIdTimestamp = Aggregation.terms("traceId", request.limit)
        .addSubAggregation(Aggregation.min("timestamp_millis"))
        .orderBy("timestamp_millis", "desc");

    List<String> indices = indexNameFormatter.indexNamePatternsForRange(beginMillis, endMillis);
    SearchRequest esRequest = SearchRequest.forIndicesAndType(indices, SPAN2)
        .filters(filters).addAggregation(traceIdTimestamp);

    HttpCall<List<String>> traceIdsCall = search.newCall(esRequest, BodyConverters.SORTED_KEYS);

    // When we receive span results, we need to group them by trace ID
    Callback<List<Span>> successCallback = new Callback<List<Span>>() {
      @Override public void onSuccess(List<Span> input) {
        List<List<Span>> traces = GroupByTraceId.apply(input, strictTraceId, true);

        // Due to tokenization of the trace ID, our matches are imprecise on Span.traceIdHigh
        for (Iterator<List<Span>> trace = traces.iterator(); trace.hasNext(); ) {
          List<Span> next = trace.next();
          if (next.get(0).traceIdHigh != 0 && !request.test(next)) {
            trace.remove();
          }
        }
        callback.onSuccess(traces);
      }

      @Override public void onError(Throwable t) {
        callback.onError(t);
      }
    };

    // Fire off the query to get spans once we have trace ids
    traceIdsCall.submit(new Callback<List<String>>() {
      @Override public void onSuccess(@Nullable List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
          callback.onSuccess(Collections.emptyList());
          return;
        }
        SearchRequest request = SearchRequest.forIndicesAndType(indices, SPAN2)
            .terms("traceId", traceIds);
        search.newCall(request, BodyConverters.SPANS).submit(successCallback);
      }

      @Override public void onError(Throwable t) {
        callback.onError(t);
      }
    });
  }

  @Override public void getTrace(long id, Callback<List<Span>> callback) {
    getTrace(0L, id, callback);
  }

  @Override public void getTrace(long traceIdHigh, long traceIdLow, Callback<List<Span>> callback) {
    getRawTrace(traceIdHigh, traceIdLow, new Callback<List<Span>>() {
      @Override public void onSuccess(@Nullable List<Span> value) {
        List<Span> result = CorrectForClockSkew.apply(MergeById.apply(value));
        callback.onSuccess(result.isEmpty() ? null : result);
      }

      @Override public void onError(Throwable t) {
        callback.onError(t);
      }
    });
  }

  @Override public void getRawTrace(long traceId, Callback<List<Span>> callback) {
    getRawTrace(0L, traceId, callback);
  }

  @Override
  public void getRawTrace(long traceIdHigh, long traceIdLow, Callback<List<Span>> callback) {
    String traceIdHex = Util.toLowerHex(strictTraceId ? traceIdHigh : 0L, traceIdLow);

    SearchRequest request = SearchRequest.forIndicesAndType(asList(allIndices), SPAN2)
        .term("traceId", traceIdHex);

    search.newCall(request, BodyConverters.NULLABLE_SPANS).submit(callback);
  }

  @Override public void getServiceNames(Callback<List<String>> callback) {
    long endMillis =  System.currentTimeMillis();
    long beginMillis =  endMillis - namesLookback;

    List<String> indices = indexNameFormatter.indexNamePatternsForRange(beginMillis, endMillis);
    // Service name queries include both local and remote endpoints. This is different than
    // Span name, as a span name can only be on a local endpoint.
    SearchRequest.Filters filters = new SearchRequest.Filters();
    filters.addRange("timestamp_millis", beginMillis, endMillis);
    SearchRequest request = SearchRequest.forIndicesAndType(indices, SPAN2)
      .filters(filters)
      .addAggregation(Aggregation.terms("localEndpoint.serviceName", Integer.MAX_VALUE))
      .addAggregation(Aggregation.terms("remoteEndpoint.serviceName", Integer.MAX_VALUE));
    search.newCall(request, BodyConverters.SORTED_KEYS).submit(callback);
  }

  @Override public void getSpanNames(String serviceName, Callback<List<String>> callback) {
    if (serviceName == null || "".equals(serviceName)) {
      callback.onSuccess(Collections.emptyList());
      return;
    }

    long endMillis =  System.currentTimeMillis();
    long beginMillis =  endMillis - namesLookback;

    List<String> indices = indexNameFormatter.indexNamePatternsForRange(beginMillis, endMillis);

    // A span name is only valid on a local endpoint, as a span name is defined locally
    SearchRequest.Filters filters = new SearchRequest.Filters()
      .addRange("timestamp_millis", beginMillis, endMillis)
      .addTerm("localEndpoint.serviceName", serviceName.toLowerCase(Locale.ROOT));

    SearchRequest request = SearchRequest.forIndicesAndType(indices, SPAN2)
      .filters(filters)
      .addAggregation(Aggregation.terms("name", Integer.MAX_VALUE));
    search.newCall(request, BodyConverters.SORTED_KEYS).submit(callback);
  }

  @Override public void getDependencies(long endTs, @Nullable Long lookback,
      Callback<List<DependencyLink>> callback) {

    long beginMillis = lookback != null ? endTs - lookback : 0;
    // We just return all dependencies in the days that fall within endTs and lookback as
    // dependency links themselves don't have timestamps.
    List<String> indices = indexNameFormatter.indexNamePatternsForRange(beginMillis, endTs);
    getDependencies(indices, callback);
  }

  void getDependencies(List<String> indices, Callback<List<DependencyLink>> callback) {
    SearchRequest request = SearchRequest.forIndicesAndType(indices, DEPENDENCY_LINK);

    search.newCall(request, BodyConverters.DEPENDENCY_LINKS).submit(callback);
  }
}
