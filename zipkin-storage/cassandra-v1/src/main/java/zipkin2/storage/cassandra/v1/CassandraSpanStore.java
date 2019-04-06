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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.Session;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.Call.FlatMapper;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.internal.AggregateCall;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanStore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.DiscreteDomain.integers;
import static zipkin2.storage.cassandra.v1.CassandraUtil.sortTraceIdsByDescTimestamp;
import static zipkin2.storage.cassandra.v1.CassandraUtil.sortTraceIdsByDescTimestampMapper;

public final class CassandraSpanStore implements SpanStore, ServiceAndSpanNames {
  private static final Logger LOG = LoggerFactory.getLogger(CassandraSpanStore.class);

  private final int maxTraceCols;
  private final int indexFetchMultiplier;
  private final boolean strictTraceId;
  private final Session session;
  private final TimestampCodec timestampCodec;
  private final Set<Integer> buckets;
  private final SelectFromTraces.Factory spans;
  private final SelectDependencies.Factory dependencies;
  private final Call<List<String>> serviceNames;
  private final SelectRemoteServiceNames.Factory remoteServiceNames;
  private final SelectSpanNames.Factory spanNames;
  private final SelectTraceIdTimestampFromServiceName.Factory selectTraceIdsByServiceName;
  private final SelectTraceIdTimestampFromServiceNames.Factory selectTraceIdsByServiceNames;
  private final SelectTraceIdTimestampFromServiceSpanName.Factory selectTraceIdsBySpanName;
  private final SelectTraceIdTimestampFromAnnotations.Factory selectTraceIdsByAnnotation;

  CassandraSpanStore(CassandraStorage storage) {
    session = storage.session();
    this.maxTraceCols = storage.maxTraceCols;
    this.indexFetchMultiplier = storage.indexFetchMultiplier;
    this.strictTraceId = storage.strictTraceId;

    ProtocolVersion protocolVersion =
        session.getCluster().getConfiguration().getProtocolOptions().getProtocolVersion();
    this.timestampCodec = new TimestampCodec(protocolVersion);
    this.buckets = ContiguousSet.create(Range.closedOpen(0, storage.bucketCount), integers());

    spans = new SelectFromTraces.Factory(session, strictTraceId, maxTraceCols);
    dependencies = new SelectDependencies.Factory(session);
    if (storage.metadata().hasRemoteService) {
      remoteServiceNames = new SelectRemoteServiceNames.Factory(session);
    } else {
      remoteServiceNames = null;
    }
    spanNames = new SelectSpanNames.Factory(session);
    serviceNames = new SelectServiceNames.Factory(session).create();

    selectTraceIdsByServiceName =
        new SelectTraceIdTimestampFromServiceName.Factory(session, timestampCodec, buckets);

    if (protocolVersion.compareTo(ProtocolVersion.V4) < 0) {
      LOG.warn("Please update Cassandra to 2.2 or later, as some features may fail");
      // Log vs failing on "Partition KEY part service_name cannot be restricted by IN relation"
      selectTraceIdsByServiceNames = null;
    } else {
      selectTraceIdsByServiceNames =
          new SelectTraceIdTimestampFromServiceNames.Factory(session, timestampCodec, buckets);
    }

    selectTraceIdsBySpanName =
        new SelectTraceIdTimestampFromServiceSpanName.Factory(session, timestampCodec);

    selectTraceIdsByAnnotation =
        new SelectTraceIdTimestampFromAnnotations.Factory(session, timestampCodec, buckets);
  }

  @Override
  public Call<List<List<Span>>> getTraces(QueryRequest request) {
    checkArgument(
        request.minDuration() == null,
        "getTraces with duration is unsupported. Upgrade to the new cassandra3 schema.");
    // Over fetch on indexes as they don't return distinct (trace id, timestamp) rows.
    final int traceIndexFetchSize = request.limit() * indexFetchMultiplier;

    List<Call<Set<Pair>>> callsToIntersect = new ArrayList<>();
    List<String> annotationKeys = CassandraUtil.annotationKeys(request);
    if (request.serviceName() != null) {
      if (request.spanName() != null) {
        callsToIntersect.add(
            this.selectTraceIdsBySpanName.newCall(
                request.serviceName(),
                request.spanName(),
                request.endTs() * 1000,
                request.lookback() * 1000,
                traceIndexFetchSize));
      } else {
        callsToIntersect.add(
            selectTraceIdsByServiceName.newCall(
                request.serviceName(),
                request.endTs() * 1000,
                request.lookback() * 1000,
                traceIndexFetchSize));
      }
      for (String annotationKey : annotationKeys) {
        callsToIntersect.add(
            selectTraceIdsByAnnotation.newCall(
                annotationKey,
                request.endTs() * 1000,
                request.lookback() * 1000,
                traceIndexFetchSize));
      }
    } else {
      checkArgument(
          selectTraceIdsByServiceNames != null,
          "getTraces without serviceName requires Cassandra 2.2 or later");
      checkArgument(annotationKeys.isEmpty(), "annotationQuery unsupported without serviceName");
      FlatMapper<List<String>, Set<Pair>> flatMapper =
          selectTraceIdsByServiceNames.newFlatMapper(
              request.endTs() * 1000, request.lookback() * 1000, traceIndexFetchSize);
      callsToIntersect.add(getServiceNames().flatMap(flatMapper));
    }

    Call<Set<Long>> traceIdCall;
    if (callsToIntersect.size() == 1) {
      traceIdCall = callsToIntersect.get(0).map(sortTraceIdsByDescTimestampMapper());
    } else {
      // We achieve the AND goal, by intersecting each of the key sets.
      traceIdCall = new IntersectTraceIds(callsToIntersect);
    }

    return traceIdCall.flatMap(spans.newFlatMapper(request));
  }

  @Override
  public Call<List<Span>> getTrace(String traceId) {
    // make sure we have a 16 or 32 character trace ID
    String normalizedTraceId = Span.normalizeTraceId(traceId);
    return spans.newCall(normalizedTraceId);
  }

  @Override
  public Call<List<String>> getServiceNames() {
    return serviceNames.clone();
  }

  @Override public Call<List<String>> getRemoteServiceNames(String serviceName) {
    if (serviceName.isEmpty() || remoteServiceNames == null) return Call.emptyList();
    return remoteServiceNames.create(serviceName);
  }

  @Override
  public Call<List<String>> getSpanNames(String serviceName) {
    if (serviceName.isEmpty()) return Call.emptyList();
    return spanNames.create(serviceName);
  }

  @Override
  public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    if (endTs <= 0) throw new IllegalArgumentException("endTs <= 0");
    if (lookback <= 0) throw new IllegalArgumentException("lookback <= 0");
    return dependencies.create(endTs, lookback);
  }

  /** The {@link Pair#left left value} of the pair is the trace ID */
  static final class IntersectTraceIds extends AggregateCall<Set<Pair>, Set<Long>> {
    IntersectTraceIds(List<Call<Set<Pair>>> calls) {
      super(calls);
    }

    @Override
    protected Set<Long> newOutput() {
      return new LinkedHashSet<>();
    }

    boolean firstInput = true;
    List<Long> inputTraceIds = new ArrayList<>();

    @Override
    protected void append(Set<Pair> input, Set<Long> output) {
      if (firstInput) {
        firstInput = false;
        output.addAll(sortTraceIdsByDescTimestamp(input));
      } else {
        inputTraceIds.clear();
        for (Pair pair : input) {
          inputTraceIds.add(pair.left);
        }
        output.retainAll(inputTraceIds);
      }
    }

    @Override
    protected boolean isEmpty(Set<Long> output) {
      return output.isEmpty();
    }

    @Override
    public IntersectTraceIds clone() {
      return new IntersectTraceIds(cloneCalls());
    }
  }
}
