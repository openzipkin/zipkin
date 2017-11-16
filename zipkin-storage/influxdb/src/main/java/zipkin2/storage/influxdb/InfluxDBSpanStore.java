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

import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
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

    q += String.format(" AND \"duration\" >= %d ", request.minDuration());
    q += String.format(" AND \"duration\" <= %d ", request.maxDuration());
    q += " GROUP BY \"trace_id\" ";
    q += String.format(" SLIMIT %d ORDER BY time DESC", request.limit());

    Query query = new Query(q, this.storage.database());
    QueryResult qresult = this.storage.get().query(query);
    throw new UnsupportedOperationException();
  }

  @Override public Call<List<Span>> getTrace(String traceId) {
    // make sure we have a 16 or 32 character trace ID
    traceId = Span.normalizeTraceId(traceId);

    // Unless we are strict, truncate the trace ID to 64bit (encoded as 16 characters)
    if (!strictTraceId && traceId.length() == 32) traceId = traceId.substring(16);

    String q =
      String.format("SELECT * FROM \"%s\" WHERE \"trace_id\" = '%s'",
        this.storage.measurement(),
        traceId);
    Query query = new Query(q, this.storage.database());
    QueryResult result = this.storage.get().query(query);
    if (result.hasError()){
      throw new RuntimeException(result.getError());
    }
    List<Span> spans = new ArrayList<>();
    QueryResult.Series series = result.getResults().get(0).getSeries().get(0);
    List<List<Object>> values = series.getValues();
    if (values != null) {
      List<String> cols = series.getColumns();
      int columnSize = cols.size();
      for (int i = 0; i < columnSize; i++) {
        String col = cols.get(i);
        Span.Builder builder = Span.newBuilder();
        Object value = values.get(i);
        String anno = "";
        Long time = -1L;
        Long duration = -1L;
        String annoKey = "";
        String endPoint = "";
        String serviceName = "";

        switch (col) {
          case "id":
            builder.id(value.toString());
            break;
          case "parent_id":
            builder.parentId(value.toString());
            break;
          case "name":
            builder.name(value.toString());
            break;
          case "service_name":
            serviceName = value.toString();
            break;
          case "annotation":
            anno = value.toString();
            break;
          case "annotation_key":
            annoKey = value.toString();
            break;
          case "endpoint_host":
            endPoint = value.toString();
            break;
          case "duration_ns":
            duration = ((Double)values.get(i).get(0)).longValue() / 1000000;
            break;
          case "time":
            time =  ((Double)values.get(i).get(0)).longValue() / 1000000;
            break;
        }

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
    }

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
          for (Object value: values){
            services.add(value.toString());
          }
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
          for (Object value: values){
            spans.add(value.toString());
          }
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
