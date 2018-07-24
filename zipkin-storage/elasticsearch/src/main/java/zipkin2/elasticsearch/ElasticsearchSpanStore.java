/*
 * Copyright 2015-2018 The OpenZipkin Authors
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

import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.client.Aggregation;
import zipkin2.elasticsearch.internal.client.SearchCallFactory;
import zipkin2.elasticsearch.internal.client.SearchRequest;
import zipkin2.storage.GroupByTraceId;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;

import java.util.*;

import static java.util.Arrays.asList;

final class ElasticsearchSpanStore implements SpanStore {

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
    //only search for SERVER span,which is simpler and faster than aggregation
    filters.addTerm("kind", "SERVER");
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

    List<String> indices = indexNameFormatter.formatTypeAndRange(SPAN, beginMillis, endMillis);
    if (indices.isEmpty()) return Call.emptyList();

    SearchRequest esRequest =
        SearchRequest.create(indices).filters(filters);
    Call<List<Span>> spanResult= search.newCall(esRequest, BodyConverters.SPANS);
    //convert to List<List<Span>>,each list has only one span
    Call<List<List<Span>>> result =spanResult.map(spanList->{
      List<List<Span>> mapperList=new LinkedList<>();
      for (Span span : spanList) {
        List<Span> singleSpanList=new LinkedList<>();
        singleSpanList.add(span);
        mapperList.add(singleSpanList);
      }
      return mapperList;
    });
    return result;
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

  @Override
  public Call<List<String>> getServiceNames() {
    if (!searchEnabled) return Call.emptyList();

    long endMillis = System.currentTimeMillis();
    long beginMillis = endMillis - namesLookback;

    List<String> indices = indexNameFormatter.formatTypeAndRange(SPAN, beginMillis, endMillis);
    if (indices.isEmpty()) return Call.emptyList();

    // Service name queries include both local and remote endpoints. This is different than
    // Span name, as a span name can only be on a local endpoint.
    SearchRequest.Filters filters = new SearchRequest.Filters();
    filters.addRange("timestamp_millis", beginMillis, endMillis);
    SearchRequest request =
        SearchRequest.create(indices)
            .filters(filters)
            .addAggregation(Aggregation.terms("localEndpoint.serviceName", Integer.MAX_VALUE))
            .addAggregation(Aggregation.terms("remoteEndpoint.serviceName", Integer.MAX_VALUE));
    return search.newCall(request, BodyConverters.KEYS);
  }

  @Override
  public Call<List<String>> getSpanNames(String serviceName) {
    if (serviceName.isEmpty() || !searchEnabled) return Call.emptyList();

    long endMillis = System.currentTimeMillis();
    long beginMillis = endMillis - namesLookback;

    List<String> indices = indexNameFormatter.formatTypeAndRange(SPAN, beginMillis, endMillis);
    if (indices.isEmpty()) return Call.emptyList();

    // A span name is only valid on a local endpoint, as a span name is defined locally
    SearchRequest.Filters filters =
        new SearchRequest.Filters()
            .addRange("timestamp_millis", beginMillis, endMillis)
            .addTerm("localEndpoint.serviceName", serviceName.toLowerCase(Locale.ROOT));

    SearchRequest request =
        SearchRequest.create(indices)
            .filters(filters)
            .addAggregation(Aggregation.terms("name", Integer.MAX_VALUE));

    return search.newCall(request, BodyConverters.KEYS);
  }

  @Override
  public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    long beginMillis = Math.max(endTs - lookback, EARLIEST_MS);

    // We just return all dependencies in the days that fall within endTs and lookback as
    // dependency links themselves don't have timestamps.
    List<String> indices = indexNameFormatter.formatTypeAndRange(DEPENDENCY, beginMillis, endTs);
    if (indices.isEmpty()) return Call.emptyList();

    return search.newCall(SearchRequest.create(indices), BodyConverters.DEPENDENCY_LINKS);
  }
}
