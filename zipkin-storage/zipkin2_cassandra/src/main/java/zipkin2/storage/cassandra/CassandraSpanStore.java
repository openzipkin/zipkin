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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.utils.UUIDs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;
import zipkin2.storage.cassandra.internal.call.AggregateIntoSet;
import zipkin2.storage.cassandra.internal.call.IntersectKeySets;

import static zipkin2.storage.cassandra.CassandraUtil.traceIdsSortedByDescTimestamp;
import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_SPAN;

final class CassandraSpanStore implements SpanStore {
  private final int maxTraceCols;
  private final int indexFetchMultiplier;
  private final boolean strictTraceId;
  private final SelectFromSpan.Factory spans;
  private final SelectDependencies.Factory dependencies;
  private final SelectSpanNames.Factory spanNames;
  private final Call<List<String>> serviceNames;
  private final int indexTtl;
  private final SelectTraceIdsFromSpan.Factory spanTable;
  private final SelectTraceIdsFromServiceSpan.Factory traceIdsFromServiceSpan;
  private final Call.Mapper<Set<Entry<String, Long>>, Map<String, Long>> collapseToMap = input -> {
    Map<String, Long> result = new LinkedHashMap<>();
    input.forEach(m -> result.put(m.getKey(), m.getValue()));
    return result;
  };

  CassandraSpanStore(CassandraStorage storage) {
    Session session = storage.session();
    maxTraceCols = storage.maxTraceCols();
    indexFetchMultiplier = storage.indexFetchMultiplier();
    strictTraceId = storage.strictTraceId();
    KeyspaceMetadata md = Schema.getKeyspaceMetadata(session);
    indexTtl = md.getTable(TABLE_TRACE_BY_SERVICE_SPAN).getOptions().getDefaultTimeToLive();

    spans = new SelectFromSpan.Factory(session, strictTraceId, maxTraceCols);
    dependencies = new SelectDependencies.Factory(session);
    spanNames = new SelectSpanNames.Factory(session);
    serviceNames = new SelectServiceNames.Factory(session).create();
    spanTable = new SelectTraceIdsFromSpan.Factory(session);
    traceIdsFromServiceSpan = new SelectTraceIdsFromServiceSpan.Factory(session);
  }

  /**
   * This fans out into a number of requests. The returned future will fail if any of the inputs
   * fail.
   *
   * <p>When {@link QueryRequest#serviceName service name} is unset, service names will be fetched
   * eagerly, implying an additional query.
   *
   * <p>The duration query is the most expensive query in cassandra, as it turns into 1 request per
   * hour of {@link QueryRequest#lookback lookback}. Because many times lookback is set to a day,
   * this means 24 requests to the backend!
   *
   * <p>See https://github.com/openzipkin/zipkin-java/issues/200
   */
  @Override
  public Call<List<List<Span>>> getTraces(QueryRequest request) {
    return strictTraceId ? doGetTraces(request) :
      doGetTraces(request).map(input -> {
        // Due to tokenization of the trace ID, our matches are imprecise on span.trace_id_high
        input.removeIf(next -> next.get(0).traceId().length() > 16 && !request.test(next));
        return input;
      });
  }

  Call<List<List<Span>>> doGetTraces(QueryRequest request) {
    TimestampRange timestampRange = timestampRange(request);
    // If we have to make multiple queries, over fetch on indexes as they don't return distinct
    // (trace id, timestamp) rows. This mitigates intersection resulting in < limit traces
    final int traceIndexFetchSize = request.limit() * indexFetchMultiplier;

    // Allows GET /api/v2/traces
    if (request.serviceName() == null && request.minDuration() == null
      && request.spanName() == null && request.annotationQuery().isEmpty()) {
      // NOTE: When we scan the span table, we can't shortcut this and just return spans that match.
      // If we did, we'd only return pieces of the trace as opposed to the entire trace.
      return spanTable.newCall(timestampRange, traceIndexFetchSize)
        .map(collapseToMap)
        .map(traceIdsSortedByDescTimestamp())
        .flatMap(spans.newFlatMapper(request.limit()));
    }

    // While a valid port of the scala cassandra span store (from zipkin 1.35), there is a fault.
    // each annotation key is an intersection, meaning we likely return < traceIndexFetchSize.
    List<Call<Map<String, Long>>> callsToIntersect = new ArrayList<>();

    List<String> annotationKeys = CassandraUtil.annotationKeys(request);
    for (String annotationKey : annotationKeys) {
      callsToIntersect.add(spanTable.newCall(
        request.serviceName(),
        annotationKey,
        timestampRange,
        traceIndexFetchSize
      ).map(collapseToMap));
    }

    // trace_by_service_span adds special empty-string keys in order to search by all
    String serviceName = null != request.serviceName() ? request.serviceName() : "";
    String spanName = null != request.spanName() ? request.spanName() : "";
    Long minDuration = request.minDuration(), maxDuration = request.maxDuration();
    int startBucket = CassandraUtil.durationIndexBucket(timestampRange.startMillis * 1000);
    int endBucket = CassandraUtil.durationIndexBucket(timestampRange.endMillis * 1000);
    if (startBucket > endBucket) {
      throw new IllegalArgumentException(
        "Start bucket (" + startBucket + ") > end bucket (" + endBucket + ")");
    }

    // TODO: ideally, the buckets are traversed backwards, only spawning queries for older buckets
    // if younger buckets are empty. This will be an async continuation, punted for now.

    List<Call<Set<Entry<String, Long>>>> bucketedTraceIdCalls = new ArrayList<>();
    for (int bucket = endBucket; bucket >= startBucket; bucket--) {
      bucketedTraceIdCalls.add(traceIdsFromServiceSpan.newCall(
        serviceName,
        spanName,
        bucket,
        minDuration,
        maxDuration,
        timestampRange,
        traceIndexFetchSize)
      );
    }
    // Unlikely, but we could have a single bucket
    callsToIntersect.add((bucketedTraceIdCalls.size() == 1
      ? bucketedTraceIdCalls.get(0)
      : new AggregateIntoSet<>(bucketedTraceIdCalls)
    ).map(collapseToMap));

    assert !callsToIntersect.isEmpty() : request + " resulted in no trace ID calls";
    if (callsToIntersect.size() == 1) {
      return callsToIntersect.get(0)
        .map(traceIdsSortedByDescTimestamp())
        .flatMap(spans.newFlatMapper(request.limit()));
    }

    // We achieve the AND goal, by intersecting each of the key sets.
    IntersectKeySets intersectedTraceIds = new IntersectKeySets(callsToIntersect);
    // @xxx the sorting by timestamp desc is broken here^
    return intersectedTraceIds.flatMap(spans.newFlatMapper(request.limit()));
  }

  @Override public Call<List<Span>> getTrace(String traceId) {
    // make sure we have a 16 or 32 character trace ID
    traceId = Span.normalizeTraceId(traceId);

    // Unless we are strict, truncate the trace ID to 64bit (encoded as 16 characters)
    if (!strictTraceId && traceId.length() == 32) traceId = traceId.substring(16);

    List<String> traceIds = Collections.singletonList(traceId);
    return spans.newCall(traceIds, maxTraceCols);
  }

  @Override public Call<List<String>> getServiceNames() {
    return serviceNames.clone();
  }

  @Override public Call<List<String>> getSpanNames(String serviceName) {
    return spanNames.create(serviceName);
  }

  @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    return dependencies.create(endTs, lookback);
  }

  // TODO(adriancole): at some later point we can refactor this out
  static List<List<Span>> groupByTraceId(Collection<Span> input, boolean strictTraceId) {
    if (input.isEmpty()) return Collections.emptyList();

    Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<>();
    for (Span span : input) {
      String traceId = strictTraceId || span.traceId().length() == 16
        ? span.traceId()
        : span.traceId().substring(16);
      if (!groupedByTraceId.containsKey(traceId)) {
        groupedByTraceId.put(traceId, new ArrayList<>());
      }
      groupedByTraceId.get(traceId).add(span);
    }
    return new ArrayList<>(groupedByTraceId.values());
  }

  static final class TimestampRange {
    long startMillis;
    UUID startUUID;
    long endMillis;
    UUID endUUID;
  }

  TimestampRange timestampRange(QueryRequest request) {
    long oldestData = Math.max(System.currentTimeMillis() - indexTtl * 1000, 0); // >= 1970
    TimestampRange result = new TimestampRange();
    result.startMillis = Math.max((request.endTs() - request.lookback()), oldestData);
    result.startUUID = UUIDs.startOf(result.startMillis);
    result.endMillis = Math.max(request.endTs(), oldestData);
    result.endUUID = UUIDs.endOf(result.endMillis);
    return result;
  }
}
