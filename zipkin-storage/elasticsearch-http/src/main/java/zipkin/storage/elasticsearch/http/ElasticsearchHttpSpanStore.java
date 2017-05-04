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
import java.util.LinkedHashMap;
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

  static final String SPAN = "span";
  static final String DEPENDENCY_LINK = "dependencylink";
  static final String SERVICE_SPAN = "servicespan";

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
      filters.addNestedTerms(asList(
          "annotations.endpoint.serviceName",
          "binaryAnnotations.endpoint.serviceName"
      ), request.serviceName);
    }

    if (request.spanName != null) {
      filters.addTerm("name", request.spanName);
    }

    for (String annotation : request.annotations) {
      Map<String, String> annotationValues = new LinkedHashMap<>();
      annotationValues.put("annotations.value", annotation);
      Map<String, String> binaryAnnotationKeys = new LinkedHashMap<>();
      binaryAnnotationKeys.put("binaryAnnotations.key", annotation);
      if (request.serviceName != null) {
        annotationValues.put("annotations.endpoint.serviceName", request.serviceName);
        binaryAnnotationKeys.put("binaryAnnotations.endpoint.serviceName", request.serviceName);
      }
      filters.addNestedTerms(annotationValues, binaryAnnotationKeys);
    }

    for (Map.Entry<String, String> kv : request.binaryAnnotations.entrySet()) {
      // In our index template, we make sure the binaryAnnotation value is indexed as string,
      // meaning non-string values won't even be indexed at all. This means that we can only
      // match string values here, which happens to be exactly what we want.
      Map<String, String> nestedTerms = new LinkedHashMap<>();
      nestedTerms.put("binaryAnnotations.key", kv.getKey());
      nestedTerms.put("binaryAnnotations.value", kv.getValue());
      if (request.serviceName != null) {
        nestedTerms.put("binaryAnnotations.endpoint.serviceName", request.serviceName);
      }
      filters.addNestedTerms(nestedTerms);
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
    SearchRequest esRequest = SearchRequest.forIndicesAndType(indices, SPAN)
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
        SearchRequest request = SearchRequest.forIndicesAndType(indices, SPAN)
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

    SearchRequest request = SearchRequest.forIndicesAndType(asList(allIndices), SPAN)
        .term("traceId", traceIdHex);

    search.newCall(request, BodyConverters.NULLABLE_SPANS).submit(callback);
  }

  @Override public void getServiceNames(Callback<List<String>> callback) {
    long endMillis =  System.currentTimeMillis();
    long beginMillis =  endMillis - namesLookback;

    List<String> indices = indexNameFormatter.indexNamePatternsForRange(beginMillis, endMillis);
    SearchRequest request = SearchRequest.forIndicesAndType(indices, SERVICE_SPAN)
        .addAggregation(Aggregation.terms("serviceName", Integer.MAX_VALUE));

    search.newCall(request, BodyConverters.SORTED_KEYS).submit(new Callback<List<String>>() {
      @Override public void onSuccess(List<String> value) {
        if (!value.isEmpty()) callback.onSuccess(value);

        // Special cased code until sites update their collectors. What this does is do a more
        // expensive nested query to get service names when the servicespan type returns nothing.
        SearchRequest.Filters filters = new SearchRequest.Filters();
        filters.addRange("timestamp_millis", beginMillis, endMillis);
        SearchRequest request = SearchRequest.forIndicesAndType(indices, SPAN)
            .filters(filters)
            .addAggregation(Aggregation.nestedTerms("annotations.endpoint.serviceName"))
            .addAggregation(Aggregation.nestedTerms("binaryAnnotations.endpoint.serviceName"));
        search.newCall(request, BodyConverters.SORTED_KEYS).submit(callback);
      }

      @Override public void onError(Throwable t) {
        callback.onError(t);
      }
    });
  }

  @Override public void getSpanNames(String serviceName, Callback<List<String>> callback) {
    if (serviceName == null || "".equals(serviceName)) {
      callback.onSuccess(Collections.emptyList());
      return;
    }

    long endMillis =  System.currentTimeMillis();
    long beginMillis =  endMillis - namesLookback;

    List<String> indices = indexNameFormatter.indexNamePatternsForRange(beginMillis, endMillis);

    SearchRequest request = SearchRequest.forIndicesAndType(indices, SERVICE_SPAN)
        .term("serviceName", serviceName.toLowerCase(Locale.ROOT))
        .addAggregation(Aggregation.terms("spanName", Integer.MAX_VALUE));

    search.newCall(request, BodyConverters.SORTED_KEYS).submit(new Callback<List<String>>() {
      @Override public void onSuccess(List<String> value) {
        if (!value.isEmpty()) callback.onSuccess(value);

        // Special cased code until sites update their collectors. What this does is do a more
        // expensive nested query to get span names when the servicespan type returns nothing.
        SearchRequest.Filters filters = new SearchRequest.Filters();
        filters.addRange("timestamp_millis", beginMillis, endMillis);
        filters.addNestedTerms(asList(
            "annotations.endpoint.serviceName",
            "binaryAnnotations.endpoint.serviceName"
        ), serviceName.toLowerCase(Locale.ROOT));
        SearchRequest request = SearchRequest.forIndicesAndType(indices, SPAN)
            .filters(filters)
            .addAggregation(Aggregation.terms("name", Integer.MAX_VALUE));
        search.newCall(request, BodyConverters.SORTED_KEYS).submit(callback);
      }

      @Override public void onError(Throwable t) {
        callback.onError(t);
      }
    });
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
