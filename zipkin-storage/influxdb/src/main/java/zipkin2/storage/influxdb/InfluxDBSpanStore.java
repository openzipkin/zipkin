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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.TimeUtil;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;

final class InfluxDBSpanStore implements SpanStore {

  final boolean strictTraceId;
  final InfluxDBStorage storage;

  InfluxDBSpanStore(InfluxDBStorage influxDB) {
    this.strictTraceId = influxDB.strictTraceId();
    this.storage = influxDB;
  }

  @Override public Call<List<List<Span>>> getTraces(QueryRequest request) {
    // TODO: spanName is optional
    String q = String.format(
      "SELECT * FROM \"%s\" WHERE \"service_name\" = '%s' AND \"name\" = '%s' AND time < %dms AND time > %dms",
      this.storage.measurement(),
      request.serviceName(),
      request.spanName(),
      request.endTs(),
      request.endTs() - request.lookback()
    );
    StringBuilder result = new StringBuilder();

    for (Iterator<Map.Entry<String, String>> i = request.annotationQuery().entrySet().iterator();
      i.hasNext(); ) {
      Map.Entry<String, String> next = i.next();
      String k = next.getKey();
      String v = next.getValue();
      if (v.isEmpty()) {
        result.append(String.format("\"annotation_key\" = '%s'", k));
      } else {
        result.append(
          String.format("(\"annotation_key\" = '%s' AND \"annotation_value\" = '%s'", k, v));
      }
      if (i.hasNext()) {
        result.append(" and ");
      }
    }
    if (result.length() > 0) {
      q += result.toString();
    }

    //q += String.format(" AND \"duration\" >= %d ", request.minDuration());
    //q += String.format(" AND \"duration\" <= %d ", request.maxDuration());
    q += " GROUP BY \"trace_id\", \"id\" ";
    q += String.format(" ORDER BY time DESC SLIMIT %d", request.limit());

    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query, TimeUnit.MILLISECONDS);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }

    List<List<Span>> results = new ArrayList<>();
    for (QueryResult.Result queryResult: response.getResults()){
      if (queryResult == null || queryResult.getSeries() == null) {
        continue;
      }
      for (QueryResult.Series traceSeries : queryResult.getSeries()){
        if (traceSeries == null) {
          continue;
        }
        List<Span> spans = spanResults(traceSeries);
        results.add(spans);
      }
    }

    return Call.create(results);
  }

  private List<Span> spanResults(QueryResult.Series series) {
    List<Span> spans = new ArrayList<>();
    List<List<Object>> values = series.getValues();
    Map<String, String> tags = series.getTags();
    if (values != null) {
      List<String> cols = series.getColumns();
      int columnSize = cols.size();
      Span.Builder builder = Span.newBuilder();
      String traceId = "";
      String id = "";
      String anno = "";
      Long time = -1L;
      Long duration = -1L;
      String annoKey = "";
      String endPoint = "";
      String serviceName = "";
      Object v = null;
      for (List<Object> value: values) {
        for (int i = 0; i < columnSize; i++) {
          String col = cols.get(i);
          switch (col) {
            case "trace_id":
              v = value.get(i);
              if (v != null) {
                traceId = v.toString();
              }
              break;
            case "id":
              v = value.get(i);
              if (v != null) {
                id = v.toString();
                id = Long.toHexString(Long.parseLong(id));
              }
              break;
            case "parent_id":
              v = value.get(i);
              if (v != null) {
                builder.parentId(Long.toHexString(Long.parseLong(v.toString())));
              }
              break;
            case "name":
              v = value.get(i);
              if (v != null) {
                builder.name(v.toString());
              }
              break;
            case "service_name":
              v = value.get(i);
              if (v != null) {
                serviceName = v.toString() ;
              }
              break;
            case "annotation":
              v = value.get(i);
              if (v != null) {
                anno = v.toString() ;
              }
              break;
            case "annotation_key":
              v = value.get(i);
              if (v != null) {
                annoKey = v.toString() ;
              }
              break;
            case "endpoint_host":
              v = value.get(i);
              if (v != null) {
                endPoint = v.toString() ;
              }
              break;
            case "duration_ns":
              duration = ((Double)(value.get(i))).longValue();
              break;
            case "time":
              time = ((Double)(value.get(i))).longValue();
              break;
          }
        }
      }
      if (id.isEmpty()) {
        id = Long.toHexString(Long.parseLong(tags.get("id")));
      }
      builder.id(id);

      if (traceId.isEmpty()){
        traceId = tags.get("trace_id");
      }
      builder.traceId(traceId);

      if (!endPoint.isEmpty() && !serviceName.isEmpty()) {
        builder.localEndpoint(
          Endpoint
            .newBuilder()
            .ip(endPoint)
            .serviceName(serviceName)
            .build());
      }

      if (!annoKey.isEmpty() && !anno.isEmpty()){
        builder.putTag(annoKey, anno);
      } else if (!anno.isEmpty()) {
        builder.addAnnotation(time, anno);
      }

      if (duration >= 0) {
        builder.duration(duration);
      }

      builder.timestamp(time);
      Span span = builder.build();

      spans.add(span);
    }
    return spans;
  }

  @Override public Call<List<Span>> getTrace(String traceId) {
    // make sure we have a 16 or 32 character trace ID
    traceId = Span.normalizeTraceId(traceId);
    long tid = Long.parseLong(traceId, 16);
    traceId = Long.toString(tid);

    // Unless we are strict, truncate the trace ID to 64bit (encoded as 16 characters)
    if (!strictTraceId && traceId.length() == 32) traceId = traceId.substring(16);

    String q =
      String.format("SELECT * FROM \"%s\" WHERE \"trace_id\" = '%s'",
        this.storage.measurement(),
        traceId);
    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query, TimeUnit.MILLISECONDS);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }
    List<QueryResult.Result> results = response.getResults();
    if (results != null && results.get(0) != null) {
      QueryResult.Result result = results.get(0);
      if (result.getSeries() != null && result.getSeries().get(0) != null) {
        QueryResult.Series series = result.getSeries().get(0);
        List<Span> spans = spanResults(series);
        return Call.create(spans);
      }
    }
    List<Span> spans = new ArrayList<>();
    return Call.create(spans);
  }

  @Override public Call<List<String>> getServiceNames() {
    String q =
      String.format("SHOW TAG VALUES FROM \"%s\" WITH KEY = \"service_name\"",
      this.storage.measurement());
    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }

    List<String> services = new ArrayList<>();
    for (QueryResult.Result result: response.getResults()){
      for (QueryResult.Series series : result.getSeries()){
        for (List<Object> values: series.getValues()) {
          services.add(values.get(1).toString());
        }
      }
    }
    return Call.create(services);
  }

  @Override public Call<List<String>> getSpanNames(String serviceName) {
    if ("".equals(serviceName)) return Call.emptyList();
    String q =
      String.format("SHOW TAG VALUES FROM \"%s\" with key=\"name\" WHERE \"service_name\" = '%s'",
      this.storage.measurement(), serviceName);
    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }

    List<String> spans = new ArrayList<>();
    for (QueryResult.Result result: response.getResults()){
      for (QueryResult.Series series : result.getSeries()){
        for (List<Object> values: series.getValues()) {
          spans.add(values.get(1).toString());
        }
      }
    }

    return Call.create(spans);
  }

  @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    String q =
      String.format("SELECT COUNT(\"duration\") FROM \"%s\"", this.storage.measurement());
    q += String.format(" WHERE time < %dms", endTs);
    q += String.format(" AND time > %dms ", endTs - lookback);
    q += "GROUP BY \"id\",\"parent_id\",time(1d)";
    Query query = new Query(q, this.storage.database());
    QueryResult result = this.storage.get().query(query);
    throw new UnsupportedOperationException();
  }
}
