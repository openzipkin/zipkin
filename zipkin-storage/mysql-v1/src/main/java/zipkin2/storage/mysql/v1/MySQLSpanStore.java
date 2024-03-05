/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.storage.GroupByTraceId;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StrictTraceId;
import zipkin2.storage.Traces;

import static zipkin2.internal.DateUtil.epochDays;
import static zipkin2.internal.HexCodec.lowerHexToUnsignedLong;

final class MySQLSpanStore implements SpanStore, Traces, ServiceAndSpanNames {

  final DataSourceCall.Factory dataSourceCallFactory;
  final Schema schema;
  final boolean strictTraceId, searchEnabled;
  final SelectSpansAndAnnotations.Factory selectFromSpansAndAnnotationsFactory;
  final Call.Mapper<List<Span>, List<List<Span>>> groupByTraceId;
  final DataSourceCall<List<String>> getServiceNamesCall;

  MySQLSpanStore(MySQLStorage storage, Schema schema) {
    this.dataSourceCallFactory = storage.dataSourceCallFactory;
    this.schema = schema;
    this.strictTraceId = storage.strictTraceId;
    this.searchEnabled = storage.searchEnabled;
    this.selectFromSpansAndAnnotationsFactory =
      new SelectSpansAndAnnotations.Factory(schema, strictTraceId);
    this.groupByTraceId = GroupByTraceId.create(strictTraceId);
    this.getServiceNamesCall = dataSourceCallFactory.create(new SelectAnnotationServiceNames());
  }

  @Override public Call<List<List<Span>>> getTraces(QueryRequest request) {
    if (!searchEnabled) return Call.emptyList();

    Call<List<List<Span>>> result =
      dataSourceCallFactory
        .create(selectFromSpansAndAnnotationsFactory.create(request))
        .map(groupByTraceId);

    return strictTraceId ? result.map(StrictTraceId.filterTraces(request)) : result;
  }

  @Override public Call<List<Span>> getTrace(String hexTraceId) {
    // make sure we have a 16 or 32 character trace ID
    hexTraceId = Span.normalizeTraceId(hexTraceId);
    long traceIdHigh = hexTraceId.length() == 32 ? lowerHexToUnsignedLong(hexTraceId, 0) : 0L;
    long traceId = lowerHexToUnsignedLong(hexTraceId);

    DataSourceCall<List<Span>> result =
      dataSourceCallFactory.create(
        selectFromSpansAndAnnotationsFactory.create(traceIdHigh, traceId));
    return strictTraceId ? result.map(StrictTraceId.filterSpans(hexTraceId)) : result;
  }

  @Override public Call<List<List<Span>>> getTraces(Iterable<String> traceIds) {
    Set<String> normalizedTraceIds = new LinkedHashSet<>();
    Set<Pair> traceIdPairs = new LinkedHashSet<>();
    for (String traceId : traceIds) {
      // make sure we have a 16 or 32 character trace ID
      String hexTraceId = Span.normalizeTraceId(traceId);
      normalizedTraceIds.add(hexTraceId);
      traceIdPairs.add(new Pair(
          hexTraceId.length() == 32 ? lowerHexToUnsignedLong(hexTraceId, 0) : 0L,
          lowerHexToUnsignedLong(hexTraceId)
        )
      );
    }

    if (traceIdPairs.isEmpty()) return Call.emptyList();
    Call<List<List<Span>>> result = dataSourceCallFactory
      .create(selectFromSpansAndAnnotationsFactory.create(traceIdPairs))
      .map(groupByTraceId);

    return strictTraceId ? result.map(StrictTraceId.filterTraces(normalizedTraceIds)) : result;
  }

  @Override public Call<List<String>> getServiceNames() {
    if (!searchEnabled) return Call.emptyList();
    return getServiceNamesCall.clone();
  }

  @Override public Call<List<String>> getRemoteServiceNames(String serviceName) {
    if (serviceName.isEmpty() || !searchEnabled || !schema.hasRemoteServiceName) {
      return Call.emptyList();
    }
    return dataSourceCallFactory.create(new SelectRemoteServiceNames(schema, serviceName));
  }

  @Override public Call<List<String>> getSpanNames(String serviceName) {
    if (serviceName.isEmpty() || !searchEnabled) return Call.emptyList();
    return dataSourceCallFactory.create(new SelectSpanNames(schema, serviceName));
  }

  @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    if (endTs <= 0) throw new IllegalArgumentException("endTs <= 0");
    if (lookback <= 0) throw new IllegalArgumentException("lookback <= 0");

    if (schema.hasPreAggregatedDependencies) {
      return dataSourceCallFactory.create(new SelectDependencies(schema, epochDays(endTs, lookback)));
    }
    return dataSourceCallFactory.create(
      new AggregateDependencies(schema, endTs * 1000 - lookback * 1000, endTs * 1000));
  }
}
