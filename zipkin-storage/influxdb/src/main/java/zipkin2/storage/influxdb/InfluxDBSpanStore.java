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

import java.util.*;
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
    String q = String.format("SELECT * FROM \"%s\" WHERE time < %dms AND time > %dms",
      this.storage.measurement(),
      request.endTs(),
      request.endTs() - request.lookback());

    if (request.spanName() != null && !request.serviceName().isEmpty()) {
        q = String.format("%s AND \"service_name\" = '%s'", q, request.serviceName());
    }

    if (request.spanName() != null && !request.spanName().isEmpty()) {
        q = String.format("%s AND \"name\" = '%s'", q, request.spanName());
    }

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

    if (request.minDuration() != null) {
      q += String.format(" AND \"duration_ns\" >= %d ", request.minDuration());
    }
    if (request.maxDuration() != null) {
      q += String.format(" AND \"duration_ns\" <= %d ", request.maxDuration());
    }
    q += " GROUP BY \"trace_id\", \"id\" ";
    q += String.format(" ORDER BY time DESC SLIMIT %d", request.limit());

    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query, TimeUnit.MICROSECONDS);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }

    Map<String,List<Span>> traces = new HashMap<String, List<Span>>();
    for (QueryResult.Result queryResult: response.getResults()){
      if (queryResult == null || queryResult.getSeries() == null) {
        continue;
      }

      for (QueryResult.Series traceSeries : queryResult.getSeries()){
        if (traceSeries == null) {
          continue;
        }
        Span span = spanResults(traceSeries);
        if (span != null) {
          if (!traces.containsKey(span.traceId())) {
            traces.put(span.traceId(), new ArrayList<>());
          }
          List<Span> spans = traces.get(span.traceId());
          spans.add(span);
          traces.put(span.traceId(), spans);
        }
      }
    }

    List<List<Span>> results = new ArrayList<>();
    for (List<Span> trace: traces.values()) {
      results.add(trace);
    }
    return Call.create(results);
  }

  private Span spanResults(QueryResult.Series series) {
    List<List<Object>> values = series.getValues();

    if (values == null) {
      return null;
    }
    Map<String, String> tags = series.getTags();
    List<String> cols = series.getColumns();
    int columnSize = cols.size();
    Span.Builder builder = Span.newBuilder();

    String id = tags.get("id");
    builder.id(id);
    builder.traceId(tags.get("trace_id"));

    for (List<Object> value: values) {

      String anno = "";
      Long time = -1L;
      String annoKey = "";
      String endPoint = "";
      String serviceName = "";
      Object v = null;

      for (int i = 0; i < columnSize; i++) {
        String col = cols.get(i);
        switch (col) {
          case "parent_id":
            v = value.get(i);
            if (v != null) {
              String parent = v.toString();
              if (!parent.equals(id)) {
                builder.parentId(parent);
              }
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
            builder.duration(((Double)(value.get(i))).longValue() / 1000);
            break;
          case "time":
            time = ((Double)(value.get(i))).longValue();
            break;
        }
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
      } else {
        builder.timestamp(time);
      }
    }

    Span span = builder.build();
    return span;
  }

  @Override public Call<List<Span>> getTrace(String traceId) {
    String q =
      String.format("SELECT * FROM \"%s\".\"%s\" WHERE \"trace_id\" = '%s' GROUP BY \"trace_id\", \"id\" ORDER BY time DESC ",
        this.storage.retentionPolicy(),
        this.storage.measurement(),
        traceId);
    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query, TimeUnit.MICROSECONDS);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }
    List<QueryResult.Result> results = response.getResults();
    List<Span> spans = new ArrayList<>();
    if (results != null && results.get(0) != null) {
      QueryResult.Result result = results.get(0);
      if (result.getSeries() != null) {
        for (QueryResult.Series series : result.getSeries()){
          Span span = spanResults(series);
          spans.add(span);
        }
      }
    }
    return Call.create(spans);
  }

  @Override public Call<List<String>> getServiceNames() {
    String q =
      String.format("SHOW TAG VALUES FROM \"%s\".\"%s\" WITH KEY = \"service_name\"",
      this.storage.retentionPolicy(),
      this.storage.measurement());
    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }

    List<String> services = new ArrayList<>();
    List<QueryResult.Result> results = response.getResults();
    if (results != null) {
      for (QueryResult.Result result : results) {
        if (result != null && result.getSeries() != null) {
          for (QueryResult.Series series : result.getSeries()) {
            for (List<Object> values : series.getValues()) {
              services.add(values.get(1).toString());
            }
          }
        }
      }
    }
    return Call.create(services);
  }

  @Override public Call<List<String>> getSpanNames(String serviceName) {
    if ("".equals(serviceName)) return Call.emptyList();
    String q =
      String.format("SHOW TAG VALUES FROM \"%s\".\"%s\" with key=\"name\" WHERE \"service_name\" = '%s'",
      this.storage.retentionPolicy(),
      this.storage.measurement(), serviceName);
    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }

    List<String> spans = new ArrayList<>();
    List<QueryResult.Result> results = response.getResults();
    if (results != null) {
      for (QueryResult.Result result : results) {
        if (result != null && result.getSeries() != null) {
          for (QueryResult.Series series : result.getSeries()) {
            for (List<Object> values : series.getValues()) {
              spans.add(values.get(1).toString());
            }
          }
        }
      }
    }

    return Call.create(spans);
  }

  @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    String q =
      String.format("SELECT COUNT(\"duration_ns\") FROM \"%s\".\"%s\"", this.storage.retentionPolicy(), this.storage.measurement());
    q += String.format(" WHERE time < %dms", endTs);
    q += String.format(" AND time > %dms ", endTs - lookback);
    q += String.format(" AND annotation='' GROUP BY \"id\",\"parent_id\",\"service_name\",time(%dms)", lookback);
    Query query = new Query(q, this.storage.database());
    QueryResult response = this.storage.get().query(query);
    if (response.hasError()){
      throw new RuntimeException(response.getError());
    }
    List<DependencyLink> links = new ArrayList<>();
    if (response.getResults() != null) {
      Map<String, String> services = new HashMap<String, String>();
      for (QueryResult.Result result : response.getResults()) {
        if (result == null) {
          continue;
        }
        for (QueryResult.Series series : result.getSeries()) {
          if (series == null) {
            continue;
          }
          Map<String, String> tags = series.getTags();
          String serviceName = tags.get("service_name");
          String id = tags.get("id");
          services.put(id, serviceName);
        }
      }

      for (QueryResult.Result result : response.getResults()) {
        if (result == null) {
          continue;
        }
        for (QueryResult.Series series : result.getSeries()) {
          if (series == null) {
            continue;
          }

          Map<String, String> tags = series.getTags();
          String child = tags.get("service_name");
          String parentID = tags.get("parent_id");
          String parent = services.get(parentID);
          long count = 0;
          for (List<Object> values : series.getValues()) {
            Object value = values.get(1);
            count += ((Double) (value)).longValue();
          }
          DependencyLink link = DependencyLink
            .newBuilder()
            .parent(parent)
            .child(child)
            .callCount(count)
            .build();

          links.add(link);
        }
      }
    }
    return Call.create(links);
  }
}
