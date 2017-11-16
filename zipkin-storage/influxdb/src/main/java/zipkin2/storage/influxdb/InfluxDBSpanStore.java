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
package zipkin2.storage.influxdb;

import java.util.List;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;

final class InfluxDBSpanStore implements SpanStore {

  final boolean strictTraceId;

  InfluxDBSpanStore(InfluxDBStorage influxDB) {
    this.strictTraceId = influxDB.strictTraceId();
  }

  @Override public Call<List<List<Span>>> getTraces(QueryRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override public Call<List<Span>> getTrace(String traceId) {
    // make sure we have a 16 or 32 character trace ID
    traceId = Span.normalizeTraceId(traceId);

    // Unless we are strict, truncate the trace ID to 64bit (encoded as 16 characters)
    if (!strictTraceId && traceId.length() == 32) traceId = traceId.substring(16);

    // SELECT * from zipkin where "trace_id"='traceId'
    throw new UnsupportedOperationException();
  }

  @Override public Call<List<String>> getServiceNames() {
    throw new UnsupportedOperationException();
  }

  @Override public Call<List<String>> getSpanNames(String serviceName) {
    if ("".equals(serviceName)) return Call.emptyList();
    // show tag values with key="serviceName"
    throw new UnsupportedOperationException();
  }

  @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    //  select count("duration") from zipkin where time > now() - 30m and time < now() group by "id","parent_id", time(1d)
    throw new UnsupportedOperationException();
  }
}
