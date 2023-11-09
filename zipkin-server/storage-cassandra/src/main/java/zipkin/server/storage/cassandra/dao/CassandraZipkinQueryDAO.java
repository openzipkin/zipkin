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

import com.datastax.oss.driver.api.core.uuid.Uuids;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;
import zipkin.server.storage.cassandra.dao.executor.MultipleTraceQueryExecutor;
import zipkin.server.storage.cassandra.dao.executor.RemoteServiceNameQueryExecutor;
import zipkin.server.storage.cassandra.dao.executor.ServiceNameQueryExecutor;
import zipkin.server.storage.cassandra.dao.executor.SingleTraceQueryExecutor;
import zipkin.server.storage.cassandra.dao.executor.SpanNameQueryExecutor;
import zipkin.server.storage.cassandra.dao.executor.TraceIDByAnnotationQueryExecutor;
import zipkin.server.storage.cassandra.dao.executor.TraceIDByServiceQueryExecutor;
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
  private final CassandraClient client;
  private final CassandraTableHelper tableHelper;
  private int indexTtl;

  private final ServiceNameQueryExecutor serviceNameQueryExecutor;
  private final RemoteServiceNameQueryExecutor remoteServiceNameQueryExecutor;
  private final SpanNameQueryExecutor spanNameQueryExecutor;
  private final SingleTraceQueryExecutor singleTraceQueryExecutor;
  private final MultipleTraceQueryExecutor multipleTraceQueryExecutor;
  private final TraceIDByAnnotationQueryExecutor traceIDByAnnotationQueryExecutor;
  private final TraceIDByServiceQueryExecutor traceIDByServiceQueryExecutor;

  public CassandraZipkinQueryDAO(CassandraClient client, CassandraTableHelper tableHelper) {
    this.client = client;
    this.tableHelper = tableHelper;

    this.serviceNameQueryExecutor = new ServiceNameQueryExecutor(client, tableHelper);
    this.remoteServiceNameQueryExecutor = new RemoteServiceNameQueryExecutor(client, tableHelper);
    this.spanNameQueryExecutor = new SpanNameQueryExecutor(client, tableHelper);
    this.singleTraceQueryExecutor = new SingleTraceQueryExecutor(client, tableHelper);
    this.multipleTraceQueryExecutor = new MultipleTraceQueryExecutor(client, tableHelper);
    this.traceIDByAnnotationQueryExecutor = new TraceIDByAnnotationQueryExecutor(client, tableHelper);
    this.traceIDByServiceQueryExecutor = new TraceIDByServiceQueryExecutor(client, tableHelper);
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
    return serviceNameQueryExecutor.get();
  }

  @Override
  public List<String> getRemoteServiceNames(String serviceName) throws IOException {
    return remoteServiceNameQueryExecutor.get(serviceName);
  }

  @Override
  public List<String> getSpanNames(String serviceName) throws IOException {
    return spanNameQueryExecutor.get(serviceName);
  }

  @Override
  public List<Span> getTrace(String traceId) {
    return singleTraceQueryExecutor.get(traceId);
  }

  @Override
  public List<List<Span>> getTraces(QueryRequest request, Duration duration) throws IOException {
    List<CompletionStage<List<String>>> completionTraceIds = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(request.annotationQuery())) {
      for (Map.Entry<String, String> entry : request.annotationQuery().entrySet()) {
        completionTraceIds.add(traceIDByAnnotationQueryExecutor.asyncGet(
            request.serviceName(), entry.getValue().isEmpty() ? entry.getKey() : entry.getKey() + "=" + entry.getValue(),
            duration.getStartTimestamp() * 1000, duration.getEndTimestamp() * 1000, request.limit()
        ));
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

    // each service names
    for (String serviceName : serviceNames) {
      for (int bucket = endBucket; bucket >= startBucket; bucket--) {
        boolean addSpanQuery = true;
        if (remoteService != null) {
          result.add(traceIDByServiceQueryExecutor.asyncWithRemoteService(
              serviceName, remoteService, bucket, timestampRange.startUUID, timestampRange.endUUID)
          );
          // If the remote service query can satisfy the request, don't make a redundant span query
          addSpanQuery = !spanName.isEmpty() || minDuration != null;
        }
        if (!addSpanQuery) continue;

        if (start_duration != null) {
          result.add(traceIDByServiceQueryExecutor.asyncWithSpanAndDuration(
              serviceName, spanName, bucket, timestampRange.startUUID, timestampRange.endUUID, start_duration, end_duration,
              request.limit()));
        } else {
          result.add(traceIDByServiceQueryExecutor.asyncWithSpan(
              serviceName, spanName, bucket, timestampRange.startUUID, timestampRange.endUUID, request.limit()));
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
    return multipleTraceQueryExecutor.get(traceIds);
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
