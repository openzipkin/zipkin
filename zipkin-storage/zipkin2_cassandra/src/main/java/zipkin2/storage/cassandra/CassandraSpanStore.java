/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
import zipkin2.storage.cassandra.internal.call.IntersectKeySets;

import static zipkin2.storage.cassandra.CassandraUtil.traceIdsSortedByDescTimestamp;
import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_SPAN;

class CassandraSpanStore implements SpanStore { // not final for testing
  private final int maxTraceCols;
  private final int indexFetchMultiplier;
  private final boolean strictTraceId, searchEnabled;
  private final SelectFromSpan.Factory spans;
  private final SelectDependencies.Factory dependencies;
  private final SelectSpanNames.Factory spanNames;
  private final Call<List<String>> serviceNames;
  private final int indexTtl;
  private final SelectTraceIdsFromSpan.Factory spanTable;
  private final SelectTraceIdsFromServiceSpan.Factory traceIdsFromServiceSpan;

  CassandraSpanStore(CassandraStorage storage) {
    Session session = storage.session();
    maxTraceCols = storage.maxTraceCols();
    indexFetchMultiplier = storage.indexFetchMultiplier();
    strictTraceId = storage.strictTraceId();
    searchEnabled = storage.searchEnabled();

    spans = new SelectFromSpan.Factory(session, strictTraceId, maxTraceCols);
    dependencies = new SelectDependencies.Factory(session);

    if (searchEnabled) {
      KeyspaceMetadata md = Schema.getKeyspaceMetadata(session);
      indexTtl = md.getTable(TABLE_TRACE_BY_SERVICE_SPAN).getOptions().getDefaultTimeToLive();

      spanNames = new SelectSpanNames.Factory(session);
      serviceNames = new SelectServiceNames.Factory(session).create();
      spanTable = new SelectTraceIdsFromSpan.Factory(session);
      traceIdsFromServiceSpan = new SelectTraceIdsFromServiceSpan.Factory(session);
    } else {
      indexTtl = 0;
      spanNames = null;
      serviceNames = null;
      spanTable = null;
      traceIdsFromServiceSpan = null;
    }
  }

  /**
   * This fans out into a number of requests corresponding to query input. In simplest case, there
   * is less than a day of data queried, and only one expression. This implies one call to fetch
   * trace IDs and another to retrieve the span details.
   *
   * <p>The amount of backend calls increase in dimensions of query complexity, days of data, and
   * limit of traces requested. For example, a query like "http.path=/foo and error" will be two
   * select statements for the expression, possibly follow-up calls for pagination (when over 5K
   * rows match). Once IDs are parsed, there's one call for each 5K rows of span data. This means
   * "http.path=/foo and error" is minimally 3 network calls, the first two in parallel.
   */
  @Override
  public Call<List<List<Span>>> getTraces(QueryRequest request) {
    if (!searchEnabled) return Call.emptyList();

    return strictTraceId ? doGetTraces(request) :
      doGetTraces(request).map(new FilterTraces(request));
  }

  Call<List<List<Span>>> doGetTraces(QueryRequest request) {
    TimestampRange timestampRange = timestampRange(request);
    // If we have to make multiple queries, over fetch on indexes as they don't return distinct
    // (trace id, timestamp) rows. This mitigates intersection resulting in < limit traces
    final int traceIndexFetchSize = request.limit() * indexFetchMultiplier;
    List<Call<Map<String, Long>>> callsToIntersect = new ArrayList<>();

    List<String> annotationKeys = CassandraUtil.annotationKeys(request);
    for (String annotationKey : annotationKeys) {
      callsToIntersect.add(spanTable.newCall(
        request.serviceName(),
        annotationKey,
        timestampRange,
        traceIndexFetchSize
      ).map(CollapseToMap.INSTANCE));
    }

    // Bucketed calls can be expensive when service name isn't specified. This guards against abuse.
    if (request.spanName() != null || request.minDuration() != null || callsToIntersect.isEmpty()) {
      Call<Set<Entry<String, Long>>> bucketedTraceIdCall =
        newBucketedTraceIdCall(request, timestampRange, traceIndexFetchSize);
      callsToIntersect.add(bucketedTraceIdCall.map(CollapseToMap.INSTANCE));
    }

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

  /**
   * Creates a call representing one or more queries against {@link Schema#TABLE_TRACE_BY_SERVICE_SPAN}.
   * The result will be an aggregate if the input requests's serviceName is null or there's more
   * than one day of data in the timestamp range.
   *
   * <p>Note that when {@link QueryRequest#serviceName()} is null, the returned query composes over
   * {@link #getServiceNames()}. This means that if you have 1000 service names, you will end up
   * with a composition of at least 1000 calls.
   */
  // TODO: smartly handle when serviceName is null. For example, rank recently written serviceNames
  // and speculatively query those first.
  Call<Set<Entry<String, Long>>> newBucketedTraceIdCall(QueryRequest request,
    TimestampRange timestampRange, int traceIndexFetchSize) {
    // trace_by_service_span adds special empty-string span name in order to search by all
    String spanName = null != request.spanName() ? request.spanName() : "";
    Long minDuration = request.minDuration(), maxDuration = request.maxDuration();
    int startBucket = CassandraUtil.durationIndexBucket(timestampRange.startMillis * 1000);
    int endBucket = CassandraUtil.durationIndexBucket(timestampRange.endMillis * 1000);
    if (startBucket > endBucket) {
      throw new IllegalArgumentException(
        "Start bucket (" + startBucket + ") > end bucket (" + endBucket + ")");
    }

    // template input with an empty service name, potentially revisiting later
    String serviceName = null != request.serviceName() ? request.serviceName() : "";

    // TODO: ideally, the buckets are traversed backwards, only spawning queries for older buckets
    // if younger buckets are empty. This will be an async continuation, punted for now.
    List<SelectTraceIdsFromServiceSpan.Input> bucketedTraceIdInputs = new ArrayList<>();
    for (int bucket = endBucket; bucket >= startBucket; bucket--) {
      bucketedTraceIdInputs.add(traceIdsFromServiceSpan.newInput(
        serviceName,
        spanName,
        bucket,
        minDuration,
        maxDuration,
        timestampRange,
        traceIndexFetchSize)
      );
    }

    Call<Set<Entry<String, Long>>> bucketedTraceIdCall;
    if ("".equals(serviceName)) {
      // If we have no service name, we have to lookup service names before running trace ID queries
      bucketedTraceIdCall = getServiceNames().flatMap(
        traceIdsFromServiceSpan.newFlatMapper(bucketedTraceIdInputs)
      );
    } else {
      bucketedTraceIdCall = traceIdsFromServiceSpan.newCall(bucketedTraceIdInputs);
    }
    return bucketedTraceIdCall;
  }

  @Override public Call<List<Span>> getTrace(String traceId) {
    // make sure we have a 16 or 32 character trace ID
    String normalizedTraceId = Span.normalizeTraceId(traceId);
    return spans.newCall(normalizedTraceId);
  }

  @Override public Call<List<String>> getServiceNames() {
    if (!searchEnabled) return Call.emptyList();
    return serviceNames.clone();
  }

  @Override public Call<List<String>> getSpanNames(String serviceName) {
    if (!searchEnabled) return Call.emptyList();
    return spanNames.create(serviceName);
  }

  @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    return dependencies.create(endTs, lookback);
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

  enum CollapseToMap implements Call.Mapper<Set<Entry<String, Long>>, Map<String, Long>> {
    INSTANCE;

    @Override public Map<String, Long> map(Set<Entry<String, Long>> input) {
      Map<String, Long> result = new LinkedHashMap<>();
      input.forEach(m -> result.put(m.getKey(), m.getValue()));
      return result;
    }

    @Override public String toString() {
      return "CollapseToMap";
    }
  }

  // Due to tokenization of the trace ID, our matches are imprecise on span.trace_id_high
  static class FilterTraces implements Call.Mapper<List<List<Span>>, List<List<Span>>> {
    final QueryRequest request;

    FilterTraces(QueryRequest request) {
      this.request = request;
    }

    @Override public List<List<Span>> map(List<List<Span>> input) {
      input.removeIf(next -> next.get(0).traceId().length() > 16 && !request.test(next));
      return input;
    }

    @Override public String toString() {
      return "FilterTraces{" + request + "}";
    }
  }
}
