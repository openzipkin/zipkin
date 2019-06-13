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
package zipkin2.storage.mysql.v1;

import java.util.List;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.storage.GroupByTraceId;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StrictTraceId;

import static zipkin2.internal.DateUtil.getDays;
import static zipkin2.internal.HexCodec.lowerHexToUnsignedLong;

final class MySQLSpanStore implements SpanStore, ServiceAndSpanNames {

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
      return dataSourceCallFactory.create(new SelectDependencies(schema, getDays(endTs, lookback)));
    }
    return dataSourceCallFactory.create(
      new AggregateDependencies(schema, endTs * 1000 - lookback * 1000, endTs * 1000));
  }
}
