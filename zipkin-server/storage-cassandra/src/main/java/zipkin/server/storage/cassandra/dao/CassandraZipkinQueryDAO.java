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

import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.uuid.Uuids;
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
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static zipkin.server.storage.cassandra.dao.CassandraTableExtension.durationIndexBucket;

public class CassandraZipkinQueryDAO implements IZipkinQueryDAO {
  private final static int NAME_QUERY_MAX_SIZE = Integer.MAX_VALUE;
  private static final Gson GSON = new Gson();

  private final CassandraClient client;
  private final CassandraTableHelper tableHelper;
  private int indexTtl;

  public CassandraZipkinQueryDAO(CassandraClient client, CassandraTableHelper tableHelper) {
    this.client = client;
    this.tableHelper = tableHelper;
  }

  private int getIndexTtl() {
    if (this.indexTtl > 0) {
      return this.indexTtl;
    }
    this.indexTtl = client.getDefaultTtl(ZipkinSpanRecord.INDEX_NAME);
    return this.indexTtl;
  }

  @Override
  public List<String> getServiceNames() throws IOException {
    return client.executeQuery("select " + ZipkinServiceTraffic.SERVICE_NAME + " from " +
            tableHelper.getTableForRead(ZipkinServiceTraffic.INDEX_NAME) + " limit " + NAME_QUERY_MAX_SIZE,
          row -> row.getString(ZipkinServiceTraffic.SERVICE_NAME));
  }

  @Override
  public List<String> getRemoteServiceNames(String serviceName) throws IOException {
    return client.executeQuery("select " + ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME +
        " from " + tableHelper.getTableForRead(ZipkinServiceRelationTraffic.INDEX_NAME) +
        " where " + ZipkinServiceRelationTraffic.SERVICE_NAME + " = ?" +
        " limit " + NAME_QUERY_MAX_SIZE,
        row -> row.getString(ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME),
        serviceName);
  }

  @Override
  public List<String> getSpanNames(String serviceName) throws IOException {
    return client.executeQuery("select " + ZipkinServiceSpanTraffic.SPAN_NAME +
        " from " + tableHelper.getTableForRead(ZipkinServiceSpanTraffic.INDEX_NAME) +
        " where " + ZipkinServiceSpanTraffic.SERVICE_NAME + " = ?" +
        " limit " + NAME_QUERY_MAX_SIZE,
        row -> row.getString(ZipkinServiceSpanTraffic.SPAN_NAME),
        serviceName);
  }

  @Override
  public List<Span> getTrace(String traceId) {
    return client.executeQuery("select * from " + tableHelper.getTableForRead(ZipkinSpanRecord.INDEX_NAME) +
          " where " + ZipkinSpanRecord.TRACE_ID + " = ?" +
          " limit " + NAME_QUERY_MAX_SIZE,
          this::buildSpan, traceId);
  }

  @Override
  public List<List<Span>> getTraces(QueryRequest request, Duration duration) throws IOException {
    List<CompletionStage<List<String>>> completionTraceIds = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(request.annotationQuery())) {
      for (Map.Entry<String, String> entry : request.annotationQuery().entrySet()) {
        completionTraceIds.add(client.executeAsyncQuery("select " + ZipkinSpanRecord.TRACE_ID +
                " from " + ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE +
                " where " + ZipkinSpanRecord.QUERY + " = ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " >= ?" +
                " and " + ZipkinSpanRecord.TIME_BUCKET + " <= ?",
            row -> row.getString(ZipkinSpanRecord.TRACE_ID),
            entry.getValue().isEmpty() ? entry.getKey() : entry.getKey() + "=" + entry.getValue(),
            duration.getStartTimeBucket(), duration.getEndTimeBucket()));
      }
    }

    // Bucketed calls can be expensive when service name isn't specified. This guards against abuse.
    if (request.remoteServiceName() != null
        || request.spanName() != null
        || request.minDuration() != null
        || completionTraceIds.isEmpty()) {
      completionTraceIds.add(newBucketedTraceIdCall(request));
    }

    final Set<String> traceIdSet = retainTraceIdList(completionTraceIds);
    return getTraces(request.limit() > 0 ? traceIdSet.stream().limit(request.limit()).collect(Collectors.toSet()) : traceIdSet);
  }

  private CompletionStage<List<String>> newBucketedTraceIdCall(QueryRequest request) throws IOException {
    final List<CompletionStage<List<String>>> result = new ArrayList<>();

    TimestampRange timestampRange = timestampRange(request);
    int startBucket = durationIndexBucket(timestampRange.startMillis);
    int endBucket = durationIndexBucket(timestampRange.endMillis);
    if (startBucket > endBucket) {
      throw new IllegalArgumentException(
          "Start bucket (" + startBucket + ") > end bucket (" + endBucket + ")");
    }

    String remoteService = request.remoteServiceName();
    List<String> serviceNames = StringUtil.isEmpty(request.serviceName()) ? getServiceNames() : Arrays.asList(request.serviceName());
    String spanName = null != request.spanName() ? request.spanName() : "";
    Long minDuration = request.minDuration(), maxDuration = request.maxDuration();

    Long start_duration = null, end_duration = null;
    if (minDuration != null) {
      start_duration = minDuration / 1000L;
      end_duration = maxDuration != null ? maxDuration / 1000L : Long.MAX_VALUE;
    }

    String traceByServiceSpanBaseCql = "select trace_id from " + CassandraTableExtension.TABLE_TRACE_BY_SERVICE_SPAN
        + " where service=? and span=? and bucket=? and ts>=? and ts<=?";
    // each service names
    for (String serviceName : serviceNames) {
      for (int bucket = endBucket; bucket >= startBucket; bucket--) {
        boolean addSpanQuery = true;
        if (remoteService != null) {
          result.add(client.executeAsyncQuery("select trace_id from " + CassandraTableExtension.TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE
              + " where service=? and remote_service=? and bucket=? and ts>=? and ts<=?",
              resultSet -> resultSet.getString(0),
              serviceName, remoteService, bucket, timestampRange.startUUID, timestampRange.endUUID));
          // If the remote service query can satisfy the request, don't make a redundant span query
          addSpanQuery = !spanName.isEmpty() || minDuration != null;
        }
        if (!addSpanQuery) continue;

        if (start_duration != null) {
          result.add(client.executeAsyncQuery(traceByServiceSpanBaseCql + " and duration>=? and duration<=?",
              resultSet -> resultSet.getString(0),
              serviceName, spanName, bucket, timestampRange.startUUID, timestampRange.endUUID, start_duration, end_duration)
              );
        } else {
          result.add(client.executeAsyncQuery(traceByServiceSpanBaseCql,
              resultSet -> resultSet.getString(0),
              serviceName, spanName, bucket, timestampRange.startUUID, timestampRange.endUUID));
        }
      }
    }

    return CompletableFuture.allOf(result.toArray(new CompletableFuture[0]))
        .thenApplyAsync(ignored ->
            result.stream()
                .map(CompletionStage::toCompletableFuture)
                .map(CompletableFuture::join)
                .collect(ArrayList::new, ArrayList::addAll, (list1, list2) -> {
                }));
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

    String table = tableHelper.getTableForRead(ZipkinSpanRecord.INDEX_NAME);
    return traceIds.stream().map(traceId ->
        client.executeAsyncQuery("select * from " + table + " where " + ZipkinSpanRecord.TRACE_ID + " = ?", this::buildSpan, traceId)
    ).map(CompletionStage::toCompletableFuture).map(CompletableFuture::join).collect(toList());
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

  static final class TimestampRange {
    long startMillis;
    UUID startUUID;
    long endMillis;
    UUID endUUID;
  }

  TimestampRange timestampRange(QueryRequest request) {
    long oldestData = Math.max(System.currentTimeMillis() - getIndexTtl() * 1000, 0); // >= 1970
    TimestampRange result = new TimestampRange();
    result.startMillis = Math.max((request.endTs() - request.lookback()), oldestData);
    result.startUUID = Uuids.startOf(result.startMillis);
    result.endMillis = Math.max(request.endTs(), oldestData);
    result.endUUID = Uuids.endOf(result.endMillis);
    return result;
  }
}
