/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.elasticsearch;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.client.Aggregation;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.elasticsearch.internal.client.SearchCallFactory;
import zipkin2.elasticsearch.internal.client.SearchRequest;
import zipkin2.storage.GroupByTraceId;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StrictTraceId;

import static java.util.Arrays.asList;

final class ElasticsearchSpanStore implements SpanStore, ServiceAndSpanNames {

  static final String SPAN = "span";
  static final String DEPENDENCY = "dependency";

  /** To not produce unnecessarily long queries, we don't look back further than first ES support */
  static final long EARLIEST_MS = 1456790400000L; // March 2016

  final SearchCallFactory search;
  final Call.Mapper<List<Span>, List<List<Span>>> groupByTraceId;
  final String[] allSpanIndices;
  final IndexNameFormatter indexNameFormatter;
  final boolean strictTraceId, searchEnabled;
  final int namesLookback;

  ElasticsearchSpanStore(ElasticsearchStorage es) {
    this.search = new SearchCallFactory(es.http());
    this.groupByTraceId = GroupByTraceId.create(es.strictTraceId());
    this.allSpanIndices = new String[] {es.indexNameFormatter().formatType(SPAN)};
    this.indexNameFormatter = es.indexNameFormatter();
    this.strictTraceId = es.strictTraceId();
    this.searchEnabled = es.searchEnabled();
    this.namesLookback = es.namesLookback();
  }

  @Override
  public Call<List<List<Span>>> getTraces(QueryRequest request) {
    if (!searchEnabled) return Call.emptyList();

    long endMillis = request.endTs();
    long beginMillis = Math.max(endMillis - request.lookback(), EARLIEST_MS);

    SearchRequest.Filters filters = new SearchRequest.Filters();
    filters.addRange("timestamp_millis", beginMillis, endMillis);
    if (request.serviceName() != null) {
      filters.addTerm("localEndpoint.serviceName", request.serviceName());
    }

    if (request.spanName() != null) {
      filters.addTerm("name", request.spanName());
    }

    for (Map.Entry<String, String> kv : request.annotationQuery().entrySet()) {
      if (kv.getValue().isEmpty()) {
        filters.addTerm("_q", kv.getKey());
      } else {
        filters.addTerm("_q", kv.getKey() + "=" + kv.getValue());
      }
    }

    if (request.minDuration() != null) {
      filters.addRange("duration", request.minDuration(), request.maxDuration());
    }

    // We need to filter to traces that contain at least one span that matches the request,
    // but the zipkin API is supposed to order traces by first span, regardless of if it was
    // filtered or not. This is not possible without either multiple, heavyweight queries
    // or complex multiple indexing, defeating much of the elegance of using elasticsearch for this.
    // So we fudge and order on the first span among the filtered spans - in practice, there should
    // be no significant difference in user experience since span start times are usually very
    // close to each other in human time.
    Aggregation traceIdTimestamp =
      Aggregation.terms("traceId", request.limit())
        .addSubAggregation(Aggregation.min("timestamp_millis"))
        .orderBy("timestamp_millis", "desc");

    List<String> indices = indexNameFormatter.formatTypeAndRange(SPAN, beginMillis, endMillis);
    if (indices.isEmpty()) return Call.emptyList();

    SearchRequest esRequest =
      SearchRequest.create(indices).filters(filters).addAggregation(traceIdTimestamp);

    HttpCall<List<String>> traceIdsCall = search.newCall(esRequest, BodyConverters.KEYS);

    Call<List<List<Span>>> result =
      traceIdsCall.flatMap(new GetSpansByTraceId(search, indices)).map(groupByTraceId);
    // Elasticsearch lookup by trace ID is by the full 128-bit length, but there's still a chance of
    // clash on lower-64 bit. When strict trace ID is enabled, we only filter client-side on clash.
    return strictTraceId ? result.map(StrictTraceId.filterTraces(request)) : result;
  }

  @Override
  public Call<List<Span>> getTrace(String traceId) {
    // make sure we have a 16 or 32 character trace ID
    traceId = Span.normalizeTraceId(traceId);

    // Unless we are strict, truncate the trace ID to 64bit (encoded as 16 characters)
    if (!strictTraceId && traceId.length() == 32) traceId = traceId.substring(16);

    SearchRequest request = SearchRequest.create(asList(allSpanIndices)).term("traceId", traceId);
    return search.newCall(request, BodyConverters.SPANS);
  }

  @Override public Call<List<String>> getServiceNames() {
    if (!searchEnabled) return Call.emptyList();

    long endMillis = System.currentTimeMillis();
    long beginMillis = endMillis - namesLookback;

    List<String> indices = indexNameFormatter.formatTypeAndRange(SPAN, beginMillis, endMillis);
    if (indices.isEmpty()) return Call.emptyList();

    SearchRequest request = SearchRequest.create(indices)
      .filters(new SearchRequest.Filters().addRange("timestamp_millis", beginMillis, endMillis))
      .addAggregation(Aggregation.terms("localEndpoint.serviceName", Integer.MAX_VALUE));
    return search.newCall(request, BodyConverters.KEYS);
  }

  @Override public Call<List<String>> getRemoteServiceNames(String serviceName) {
    return aggregatedFieldByServiceName(serviceName, "remoteEndpoint.serviceName");
  }

  @Override public Call<List<String>> getSpanNames(String serviceName) {
    return aggregatedFieldByServiceName(serviceName, "name");
  }

  Call<List<String>> aggregatedFieldByServiceName(String serviceName, String term) {
    if (serviceName.isEmpty() || !searchEnabled) return Call.emptyList();

    long endMillis = System.currentTimeMillis();
    long beginMillis = endMillis - namesLookback;

    List<String> indices = indexNameFormatter.formatTypeAndRange(SPAN, beginMillis, endMillis);
    if (indices.isEmpty()) return Call.emptyList();

    // A span name is only valid on a local endpoint, as a span name is defined locally
    SearchRequest.Filters filters = new SearchRequest.Filters()
      .addRange("timestamp_millis", beginMillis, endMillis)
      .addTerm("localEndpoint.serviceName", serviceName.toLowerCase(Locale.ROOT));

    SearchRequest request = SearchRequest.create(indices).filters(filters)
      .addAggregation(Aggregation.terms(term, Integer.MAX_VALUE));

    return search.newCall(request, BodyConverters.KEYS);
  }

  @Override
  public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    if (endTs <= 0) throw new IllegalArgumentException("endTs <= 0");
    if (lookback <= 0) throw new IllegalArgumentException("lookback <= 0");

    long beginMillis = Math.max(endTs - lookback, EARLIEST_MS);

    // We just return all dependencies in the days that fall within endTs and lookback as
    // dependency links themselves don't have timestamps.
    List<String> indices = indexNameFormatter.formatTypeAndRange(DEPENDENCY, beginMillis, endTs);
    if (indices.isEmpty()) return Call.emptyList();

    return search.newCall(SearchRequest.create(indices), BodyConverters.DEPENDENCY_LINKS);
  }

  static final class GetSpansByTraceId implements Call.FlatMapper<List<String>, List<Span>> {
    final SearchCallFactory search;
    final List<String> indices;

    GetSpansByTraceId(SearchCallFactory search, List<String> indices) {
      this.search = search;
      this.indices = indices;
    }

    @Override
    public Call<List<Span>> map(List<String> input) {
      if (input.isEmpty()) return Call.emptyList();

      SearchRequest getTraces = SearchRequest.create(indices).terms("traceId", input);
      return search.newCall(getTraces, BodyConverters.SPANS);
    }

    @Override
    public String toString() {
      return "GetSpansByTraceId{indices=" + indices + "}";
    }
  }
}
