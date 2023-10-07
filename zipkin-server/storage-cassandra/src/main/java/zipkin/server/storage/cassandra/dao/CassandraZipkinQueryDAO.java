/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.storage.cassandra.dao;

import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public class CassandraZipkinQueryDAO implements IZipkinQueryDAO {
  private final static int NAME_QUERY_MAX_SIZE = Integer.MAX_VALUE;
  private static final Gson GSON = new Gson();

  private final CassandraClient client;
  private final CassandraTableHelper tableHelper;

  public CassandraZipkinQueryDAO(CassandraClient client, CassandraTableHelper tableHelper) {
    this.client = client;
    this.tableHelper = tableHelper;
  }

  @Override
  public List<String> getServiceNames() throws IOException {
    final List<String> services = new ArrayList<>();

    for (String table : tableHelper.getTablesWithinTTL(ZipkinServiceTraffic.INDEX_NAME)) {
      services.addAll(client.executeQuery("select " + ZipkinServiceTraffic.SERVICE_NAME + " from " + table + " limit " + NAME_QUERY_MAX_SIZE,
          row -> row.getString(ZipkinServiceTraffic.SERVICE_NAME)));
    }

    return services
        .stream()
        .limit(NAME_QUERY_MAX_SIZE)
        .collect(toList());
  }

  @Override
  public List<String> getRemoteServiceNames(String serviceName) throws IOException {
    final Set<String> services = new HashSet<>();

    for (String table : tableHelper.getTablesWithinTTL(ZipkinServiceRelationTraffic.INDEX_NAME)) {
      services.addAll(client.executeQuery("select " + ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME +
          " from " + table +
          " where " + ZipkinServiceRelationTraffic.SERVICE_NAME + " = ?" +
          " limit " + NAME_QUERY_MAX_SIZE,
          row -> row.getString(ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME),
          serviceName));
    }

    return services
        .stream()
        .limit(NAME_QUERY_MAX_SIZE)
        .collect(toList());
  }

  @Override
  public List<String> getSpanNames(String serviceName) throws IOException {
    final Set<String> names = new HashSet<>();

    for (String table : tableHelper.getTablesWithinTTL(ZipkinServiceSpanTraffic.INDEX_NAME)) {
      names.addAll(client.executeQuery("select " + ZipkinServiceSpanTraffic.SPAN_NAME +
          " from " + table +
          " where " + ZipkinServiceSpanTraffic.SERVICE_NAME + " = ?" +
          " limit " + NAME_QUERY_MAX_SIZE,
          row -> row.getString(ZipkinServiceSpanTraffic.SPAN_NAME),
          serviceName));
    }

    return names
        .stream()
        .limit(NAME_QUERY_MAX_SIZE)
        .collect(toList());
  }

  @Override
  public List<Span> getTrace(String traceId) {
    final List<Span> spans = new ArrayList<>();

    for (String table : tableHelper.getTablesWithinTTL(ZipkinSpanRecord.INDEX_NAME)) {
      spans.addAll(client.executeQuery("select * from " + table +
          " where " + ZipkinSpanRecord.TRACE_ID + " = ?" +
          " limit " + NAME_QUERY_MAX_SIZE,
          this::buildSpan, traceId));
    }

    return spans;
  }

  @Override
  public List<List<Span>> getTraces(QueryRequest request, Duration duration) throws IOException {
    Set<String> traceIdSet = new HashSet<>();
    for (String table : tableHelper.getTablesForRead(
        ZipkinSpanRecord.INDEX_NAME,
        duration.getStartTimeBucket(),
        duration.getEndTimeBucket()
    )) {
      List<CompletionStage<List<String>>> completionTraceIds = new ArrayList<>();
      if (CollectionUtils.isNotEmpty(request.annotationQuery())) {
        final long timeBucket = TableHelper.getTimeBucket(table);
        final String tagTable = TableHelper.getTable(ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE, timeBucket);
        for (Map.Entry<String, String> entry : request.annotationQuery().entrySet()) {
          completionTraceIds.add(client.executeAsyncQuery("select " + ZipkinSpanRecord.TRACE_ID + " from " + tagTable +
                  " where " + ZipkinSpanRecord.QUERY + " = ?" +
                  " and " + ZipkinSpanRecord.TIME_BUCKET + " >= ?" +
                  " and " + ZipkinSpanRecord.TIME_BUCKET + " <= ? ALLOW FILTERING",
              row -> row.getString(ZipkinSpanRecord.TRACE_ID),
              entry.getValue().isEmpty() ? entry.getKey() : entry.getKey() + "=" + entry.getValue(),
              duration.getStartTimeBucket(), duration.getEndTimeBucket()));
        }
      }
      if (request.minDuration() != null) {
        completionTraceIds.add(client.executeAsyncQuery("select " + ZipkinSpanRecord.TRACE_ID + " from " + table +
                " where " + ZipkinSpanRecord.DURATION + " >= ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " >= ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " <= ? ALLOW FILTERING",
            row -> row.getString(ZipkinSpanRecord.TRACE_ID),
            request.minDuration(), duration.getStartTimeBucket(), duration.getEndTimeBucket()
        ));
      }
      if (request.maxDuration() != null) {
        completionTraceIds.add(client.executeAsyncQuery("select " + ZipkinSpanRecord.TRACE_ID + " from " + table +
                " where " + ZipkinSpanRecord.DURATION + " <= ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " >= ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " <= ? ALLOW FILTERING",
            row -> row.getString(ZipkinSpanRecord.TRACE_ID),
            request.maxDuration(), duration.getStartTimeBucket(), duration.getEndTimeBucket()
        ));
      }
      if (StringUtil.isNotEmpty(request.serviceName())) {
        completionTraceIds.add(client.executeAsyncQuery("select " + ZipkinSpanRecord.TRACE_ID + " from " + table +
                " where " + ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME + " = ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " >= ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " <= ? ALLOW FILTERING",
            row -> row.getString(ZipkinSpanRecord.TRACE_ID),
            request.serviceName(), duration.getStartTimeBucket(), duration.getEndTimeBucket()
        ));
      }
      if (StringUtil.isNotEmpty(request.remoteServiceName())) {
        completionTraceIds.add(client.executeAsyncQuery("select " + ZipkinSpanRecord.TRACE_ID + " from " + table +
                " where " + ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME + " = ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " >= ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " <= ? ALLOW FILTERING",
            row -> row.getString(ZipkinSpanRecord.TRACE_ID),
            request.remoteServiceName(), duration.getStartTimeBucket(), duration.getEndTimeBucket()
        ));
      }
      if (StringUtil.isNotEmpty(request.spanName())) {
        completionTraceIds.add(client.executeAsyncQuery("select " + ZipkinSpanRecord.TRACE_ID + " from " + table +
                " where " + ZipkinSpanRecord.NAME + " = ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " >= ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " <= ? ALLOW FILTERING",
            row -> row.getString(ZipkinSpanRecord.TRACE_ID),
            request.spanName(), duration.getStartTimeBucket(), duration.getEndTimeBucket()
        ));
      }

      traceIdSet.addAll(retainTraceIdList(completionTraceIds));
    }

    return getTraces(request.limit() > 0 ? traceIdSet.stream().limit(request.limit()).collect(Collectors.toSet()) : traceIdSet);
  }

  private Set<String> retainTraceIdList(List<CompletionStage<List<String>>> completionStages) {
    Iterator<CompletionStage<List<String>>> iterator = completionStages.iterator();
    if (!iterator.hasNext()) return Collections.emptySet();
    Set<String> result = new HashSet<>(iterator.next().toCompletableFuture().join());
    while (iterator.hasNext() && result.size() > 0) {
      Set<String> nextSet = new HashSet<>(iterator.next().toCompletableFuture().join());
      result.retainAll(nextSet);
    }

    return result;
  }

  @Override
  public List<List<Span>> getTraces(Set<String> traceIds) throws IOException {
    if (CollectionUtils.isEmpty(traceIds)) {
      return Collections.emptyList();
    }

    final List<List<Span>> result = new ArrayList<>();
    for (String table : tableHelper.getTablesWithinTTL(ZipkinSpanRecord.INDEX_NAME)) {
      final PreparedStatement stmt = client.getSession().prepare("select * from " + table + " where " +
          ZipkinSpanRecord.TRACE_ID + " in ?");
      final ResultSet execute = client.getSession().execute(stmt.boundStatementBuilder()
          .setList(0, new ArrayList<>(traceIds), String.class).build());

      result.addAll(StreamSupport.stream(execute.spliterator(), false)
          .map(this::buildSpan).collect(Collectors.toMap(Span::traceId, s -> new ArrayList<>(Collections.singleton(s)), (s1, s2) -> {
            s1.addAll(s2);
            return s1;
          })).values());
    }
    return result;
  }

  private Span buildSpan(Row row) {
    Span.Builder span = Span.newBuilder();
    span.traceId(row.getString(ZipkinSpanRecord.TRACE_ID));
    span.id(row.getString(ZipkinSpanRecord.SPAN_ID));
    span.parentId(row.getString(ZipkinSpanRecord.PARENT_ID));
    String kind = row.getString(ZipkinSpanRecord.KIND);
    if (!StringUtil.isEmpty(kind)) {
      span.kind(Span.Kind.valueOf(kind));
    }
    span.timestamp(row.getLong(ZipkinSpanRecord.TIMESTAMP));
    span.duration(row.getLong(ZipkinSpanRecord.DURATION));
    span.name(row.getString(ZipkinSpanRecord.NAME));

    if (row.getInt(ZipkinSpanRecord.DEBUG) > 0) {
      span.debug(Boolean.TRUE);
    }
    if (row.getInt(ZipkinSpanRecord.SHARED) > 0) {
      span.shared(Boolean.TRUE);
    }
    //Build localEndpoint
    Endpoint.Builder localEndpoint = Endpoint.newBuilder();
    localEndpoint.serviceName(row.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME));
    if (!StringUtil.isEmpty(row.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV4))) {
      localEndpoint.parseIp(row.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV4));
    } else {
      localEndpoint.parseIp(row.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV6));
    }
    localEndpoint.port(row.getInt(ZipkinSpanRecord.LOCAL_ENDPOINT_PORT));
    span.localEndpoint(localEndpoint.build());
    //Build remoteEndpoint
    Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
    remoteEndpoint.serviceName(row.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME));
    if (!StringUtil.isEmpty(row.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV4))) {
      remoteEndpoint.parseIp(row.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV4));
    } else {
      remoteEndpoint.parseIp(row.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV6));
    }
    remoteEndpoint.port(row.getInt(ZipkinSpanRecord.REMOTE_ENDPOINT_PORT));
    span.remoteEndpoint(remoteEndpoint.build());

    //Build tags
    String tagsString = row.getString(ZipkinSpanRecord.TAGS);
    if (!StringUtil.isEmpty(tagsString)) {
      JsonObject tagsJson = GSON.fromJson(tagsString, JsonObject.class);
      for (Map.Entry<String, JsonElement> tag : tagsJson.entrySet()) {
        span.putTag(tag.getKey(), tag.getValue().getAsString());
      }
    }
    //Build annotation
    String annotationString = row.getString(ZipkinSpanRecord.ANNOTATIONS);
    if (!StringUtil.isEmpty(annotationString)) {
      JsonObject annotationJson = GSON.fromJson(annotationString, JsonObject.class);
      for (Map.Entry<String, JsonElement> annotation : annotationJson.entrySet()) {
        span.addAnnotation(Long.parseLong(annotation.getKey()), annotation.getValue().getAsString());
      }
    }
    return span.build();
  }
}
