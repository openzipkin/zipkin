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

import com.squareup.moshi.JsonReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import okhttp3.Response;
import okio.BufferedSource;
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
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.client.Aggregation;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.elasticsearch.internal.client.HttpCall.BodyConverter;
import zipkin2.elasticsearch.internal.client.SearchCallFactory;
import zipkin2.elasticsearch.internal.client.SearchRequest;
import zipkin2.elasticsearch.internal.client.SearchResultConverter;

import static java.util.Arrays.asList;
import static zipkin.internal.Util.propagateIfFatal;
import static zipkin2.elasticsearch.internal.JsonReaders.collectValuesNamed;
import static zipkin2.elasticsearch.internal.client.HttpCall.parseResponse;

final class LegacyElasticsearchHttpSpanStore implements AsyncSpanStore {
  static final String SPAN = "span";
  static final String DEPENDENCY_LINK = "dependencylink";
  static final String SERVICE_SPAN = "servicespan";
  /** To not produce unnecessarily long queries, we don't look back further than first ES support */
  static final long EARLIEST_MS = 1456790400000L; // March 2016
  static final HttpCall.BodyConverter<List<String>> KEYS = new BodyConverter<List<String>>() {
    @Override public List<String> convert(BufferedSource b) throws IOException {
      return collectValuesNamed(JsonReader.of(b), "key");
    }
  };
  static final BodyConverter<List<Span>> SPANS =
    SearchResultConverter.create(LegacyJsonAdapters.SPAN_ADAPTER);
  static final BodyConverter<List<Span>> NULLABLE_SPANS =
    SearchResultConverter.create(LegacyJsonAdapters.SPAN_ADAPTER).defaultToNull();
  static final BodyConverter<List<DependencyLink>> DEPENDENCY_LINKS =
    new SearchResultConverter<DependencyLink>(LegacyJsonAdapters.LINK_ADAPTER) {
      @Override public List<DependencyLink> convert(BufferedSource content) throws IOException {
        List<DependencyLink> result = super.convert(content);
        return result.isEmpty() ? result : zipkin.internal.DependencyLinker.merge(result);
      }
    };

  final SearchCallFactory search;
  final String[] allIndices;
  final IndexNameFormatter indexNameFormatter;
  final boolean strictTraceId;
  final int namesLookback;

  LegacyElasticsearchHttpSpanStore(ElasticsearchStorage es) {
    this.search = new SearchCallFactory(es.http());
    this.allIndices = new String[] {es.indexNameFormatter().formatType(null)};
    this.indexNameFormatter = es.indexNameFormatter();
    this.strictTraceId = es.strictTraceId();
    this.namesLookback = es.namesLookback();
  }

  @Override public void getTraces(QueryRequest request, Callback<List<List<Span>>> callback) {
    long endMillis = request.endTs;
    long beginMillis = Math.max(endMillis - request.lookback, EARLIEST_MS);

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

    List<String> indices = indexNameFormatter.formatTypeAndRange(null, beginMillis, endMillis);
    if (indices.isEmpty()) {
      callback.onSuccess(Collections.emptyList());
      return;
    }

    SearchRequest esRequest = SearchRequest.create(indices, SPAN)
      .filters(filters).addAggregation(traceIdTimestamp);

    HttpCall<List<String>> traceIdsCall = search.newCall(esRequest, KEYS);

    // When we receive span results, we need to group them by trace ID
    Callback<List<Span>> successCallback = new Callback<List<Span>>() {
      @Override public void onSuccess(@Nullable List<Span> input) {
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
    submit(traceIdsCall, new Callback<List<String>>() {
      @Override public void onSuccess(@Nullable List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
          callback.onSuccess(Collections.emptyList());
          return;
        }
        SearchRequest request = SearchRequest.create(indices, SPAN).terms("traceId", traceIds);
        submit(search.newCall(request, SPANS), successCallback);
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

    SearchRequest request = SearchRequest.create(asList(allIndices), SPAN)
      .term("traceId", traceIdHex);

    submit(search.newCall(request, NULLABLE_SPANS), callback);
  }

  @Override public void getServiceNames(Callback<List<String>> callback) {
    long endMillis = System.currentTimeMillis();
    long beginMillis = endMillis - namesLookback;

    List<String> indices = indexNameFormatter.formatTypeAndRange(null, beginMillis, endMillis);
    if (indices.isEmpty()) {
      callback.onSuccess(Collections.emptyList());
      return;
    }

    SearchRequest request = SearchRequest.create(indices, SERVICE_SPAN)
      .addAggregation(Aggregation.terms("serviceName", Integer.MAX_VALUE));

    submit(search.newCall(request, KEYS), new Callback<List<String>>() {
      @Override public void onSuccess(@Nullable List<String> value) {
        if (!value.isEmpty()) callback.onSuccess(value);

        // Special cased code until sites update their collectors. What this does is do a more
        // expensive nested query to get service names when the servicespan type returns nothing.
        SearchRequest.Filters filters = new SearchRequest.Filters();
        filters.addRange("timestamp_millis", beginMillis, endMillis);
        SearchRequest request = SearchRequest.create(indices, SPAN).filters(filters)
          .addAggregation(Aggregation.nestedTerms("annotations.endpoint.serviceName"))
          .addAggregation(Aggregation.nestedTerms("binaryAnnotations.endpoint.serviceName"));
        submit(search.newCall(request, KEYS), callback);
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

    long endMillis = System.currentTimeMillis();
    long beginMillis = endMillis - namesLookback;

    List<String> indices = indexNameFormatter.formatTypeAndRange(null, beginMillis, endMillis);
    if (indices.isEmpty()) {
      callback.onSuccess(Collections.emptyList());
      return;
    }

    SearchRequest request = SearchRequest.create(indices, SERVICE_SPAN)
      .term("serviceName", serviceName.toLowerCase(Locale.ROOT))
      .addAggregation(Aggregation.terms("spanName", Integer.MAX_VALUE));

    submit(search.newCall(request, KEYS), new Callback<List<String>>() {
      @Override public void onSuccess(@Nullable List<String> value) {
        if (!value.isEmpty()) callback.onSuccess(value);

        // Special cased code until sites update their collectors. What this does is do a more
        // expensive nested query to get span names when the servicespan type returns nothing.
        SearchRequest.Filters filters = new SearchRequest.Filters();
        filters.addRange("timestamp_millis", beginMillis, endMillis);
        filters.addNestedTerms(asList(
          "annotations.endpoint.serviceName",
          "binaryAnnotations.endpoint.serviceName"
        ), serviceName.toLowerCase(Locale.ROOT));
        SearchRequest request = SearchRequest.create(indices, SPAN).filters(filters)
          .addAggregation(Aggregation.terms("name", Integer.MAX_VALUE));
        submit(search.newCall(request, KEYS), callback);
      }

      @Override public void onError(Throwable t) {
        callback.onError(t);
      }
    });
  }

  @Override public void getDependencies(long endTs, @Nullable Long lookback,
    Callback<List<DependencyLink>> callback) {
    long beginMillis = lookback != null ? Math.max(endTs - lookback, EARLIEST_MS) : EARLIEST_MS;

    // We just return all dependencies in the days that fall within endTs and lookback as
    // dependency links themselves don't have timestamps.
    List<String> indices = indexNameFormatter.formatTypeAndRange(null, beginMillis, endTs);
    if (indices.isEmpty()) {
      callback.onSuccess(Collections.emptyList());
      return;
    }

    getDependencies(indices, callback);
  }

  void getDependencies(List<String> indices, Callback<List<DependencyLink>> callback) {
    SearchRequest request = SearchRequest.create(indices, DEPENDENCY_LINK);

    submit(search.newCall(request, DEPENDENCY_LINKS), callback);
  }

  static <V> void submit(HttpCall<V> call, Callback<V> delegate) {
    call.call.enqueue(new CallbackAdapter<V>(call.bodyConverter, delegate));
  }

  static class CallbackAdapter<V> implements okhttp3.Callback {
    final HttpCall.BodyConverter<V> bodyConverter;
    final Callback<V> delegate;

    CallbackAdapter(HttpCall.BodyConverter<V> bodyConverter, Callback<V> delegate) {
      this.bodyConverter = bodyConverter;
      this.delegate = delegate;
    }

    @Override public void onFailure(okhttp3.Call call, IOException e) {
      delegate.onError(e);
    }

    /** Note: this runs on the {@link okhttp3.OkHttpClient#dispatcher() dispatcher} thread! */
    @Override public void onResponse(okhttp3.Call call, Response response) {
      try {
        delegate.onSuccess(parseResponse(response, bodyConverter));
      } catch (Throwable e) {
        propagateIfFatal(e);
        delegate.onError(e);
      }
    }
  }
}
