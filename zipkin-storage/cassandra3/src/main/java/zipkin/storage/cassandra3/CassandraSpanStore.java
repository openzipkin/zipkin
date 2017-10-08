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
package zipkin.storage.cassandra3;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.UUIDs;
import com.google.common.base.Function;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zipkin2.internal.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.internal.DependencyLinker;
import zipkin2.storage.SpanStore;
import zipkin2.storage.QueryRequest;
import zipkin.storage.cassandra3.Schema.AnnotationUDT;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.DiscreteDomain.integers;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transformAsync;
import static zipkin.internal.Util.getDays;
import static zipkin.storage.cassandra3.Schema.TABLE_SERVICE_SPANS;
import static zipkin.storage.cassandra3.Schema.TABLE_TRACES;
import static zipkin.storage.cassandra3.Schema.TABLE_TRACE_BY_SERVICE_SPAN;

final class CassandraSpanStore implements SpanStore {
  private static final Logger LOG = LoggerFactory.getLogger(CassandraSpanStore.class);

  static final Call<List<String>> EMPTY_LIST = Call.emptyList();

  private final int maxTraceCols;
  private final int indexFetchMultiplier;
  private final boolean strictTraceId;
  private final Session session;
  private final PreparedStatement selectTraces;
  private final PreparedStatement selectDependencies;
  private final PreparedStatement selectServiceNames;
  private final PreparedStatement selectSpanNames;
  private final PreparedStatement selectTraceIdsByServiceSpanName;
  private final PreparedStatement selectTraceIdsByServiceSpanNameAndDuration;
  private final PreparedStatement selectTraceIdsByAnnotation;
  private final Function<Row, Map.Entry<String, Long>> traceIdToTimestamp;
  private final Function<Row, Map.Entry<String, Long>> traceIdToLong;
  private final Function<Row, String> rowToSpanName;
  private final Function<Row, String> rowToServiceName;
  private final Function<Row, Span> rowToSpan;
  private final Function<List<Map<String, Long>>, Map<String, Long>> collapseTraceIdMaps;
  private final int indexTtl;

  CassandraSpanStore(Session session, int maxTraceCols, int indexFetchMultiplier, boolean strictTraceId) {
    this.session = session;
    this.maxTraceCols = maxTraceCols;
    this.indexFetchMultiplier = indexFetchMultiplier;
    this.strictTraceId = strictTraceId;

    selectTraces = session.prepare(
        QueryBuilder.select(
                "trace_id", "id", "ts", "span", "parent_id",
                "duration", "l_ep", "r_ep", "annotations", "tags", "shared")
            .from(TABLE_TRACES)
            .where(QueryBuilder.in("trace_id", QueryBuilder.bindMarker("trace_id")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectDependencies = session.prepare(
        QueryBuilder.select("parent", "child", "errors", "calls")
            .from(Schema.TABLE_DEPENDENCIES)
            .where(QueryBuilder.in("day", QueryBuilder.bindMarker("days"))));

    selectServiceNames = session.prepare(
        QueryBuilder.select("service")
            .distinct()
            .from(TABLE_SERVICE_SPANS));

    selectSpanNames = session.prepare(
        QueryBuilder.select("span")
            .from(TABLE_SERVICE_SPANS)
            .where(QueryBuilder.eq("service", QueryBuilder.bindMarker("service")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectTraceIdsByServiceSpanName = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(TABLE_TRACE_BY_SERVICE_SPAN)
            .where(QueryBuilder.eq("service", QueryBuilder.bindMarker("service")))
            .and(QueryBuilder.eq("span", QueryBuilder.bindMarker("span")))
            .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectTraceIdsByServiceSpanNameAndDuration = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(TABLE_TRACE_BY_SERVICE_SPAN)
            .where(QueryBuilder.eq("service", QueryBuilder.bindMarker("service")))
            .and(QueryBuilder.eq("span", QueryBuilder.bindMarker("span")))
            .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .and(QueryBuilder.gte("duration", QueryBuilder.bindMarker("start_duration")))
            .and(QueryBuilder.lte("duration", QueryBuilder.bindMarker("end_duration")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectTraceIdsByAnnotation = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(TABLE_TRACES)
            .where(QueryBuilder.eq("l_service", QueryBuilder.bindMarker("l_service")))
            .and(QueryBuilder.like("annotation_query", QueryBuilder.bindMarker("annotation_query")))
            .and(QueryBuilder.gte("ts_uuid", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts_uuid", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .allowFiltering());

    traceIdToTimestamp = row ->
      new AbstractMap.SimpleEntry<>(
          row.getString("trace_id"),
          UUIDs.unixTimestamp(row.getUUID("ts")));

    traceIdToLong = row ->
        new AbstractMap.SimpleEntry<>(
            row.getString("trace_id"),
            row.getLong("ts"));

    rowToSpanName = row -> row.getString("span");

    rowToServiceName = row -> row.getString("service");

    rowToSpan = row -> {
      String traceId = row.getString("trace_id");
      Span.Builder builder = Span.newBuilder()
          .traceId(traceId)
          .id(row.getString("id"))
          .name(row.getString("span"))
          .duration(row.getLong("duration"));

      if (!row.isNull("ts")) {
        builder = builder.timestamp(row.getLong("ts"));
      }
      if (!row.isNull("duration")) {
        builder = builder.duration(row.getLong("duration"));
      }
      if (!row.isNull("parent_id")) {
        builder = builder.parentId(row.getString("parent_id"));
      }
      if (!row.isNull("l_ep")) {
        builder = builder.localEndpoint(row.get("l_ep", Schema.EndpointUDT.class).toEndpoint());
      }
      if (!row.isNull("r_ep")) {
        builder = builder.remoteEndpoint(row.get("r_ep", Schema.EndpointUDT.class).toEndpoint());
      }
      if (!row.isNull("shared")) {
        builder = builder.shared(row.getBool("shared"));
      }
      for (AnnotationUDT udt : row.getList("annotations", AnnotationUDT.class)) {
        builder = builder.addAnnotation(udt.toAnnotation().timestamp(), udt.toAnnotation().value());
      }
      for (Map.Entry<String,String> tag : row.getMap("tags", String.class, String.class).entrySet()) {
        builder = builder.putTag(tag.getKey(), tag.getValue());
      }
      return builder.build();
    };

    collapseTraceIdMaps = input -> {
      Map<String, Long> result = new LinkedHashMap<>();
      input.forEach(m -> result.putAll(m));
      return result;
    };

    KeyspaceMetadata md = Schema.getKeyspaceMetadata(session);
    this.indexTtl = md.getTable(TABLE_TRACE_BY_SERVICE_SPAN).getOptions().getDefaultTimeToLive();
  }

  /**
   * This fans out into a number of requests. The returned future will fail if any of the
   * inputs fail.
   *
   * <p>When {@link QueryRequest#serviceName service name} is unset, service names will be
   * fetched eagerly, implying an additional query.
   *
   * <p>The duration query is the most expensive query in cassandra, as it turns into 1 request per
   * hour of {@link QueryRequest#lookback lookback}. Because many times lookback is set to a day,
   * this means 24 requests to the backend!
   *
   * <p>See https://github.com/openzipkin/zipkin-java/issues/200
   */
  @Override
  public Call<List<List<Span>>> getTraces(final QueryRequest request) {
    // Over fetch on indexes as they don't return distinct (trace id, timestamp) rows.
    final int traceIndexFetchSize = request.limit() * indexFetchMultiplier;
    ListenableFuture<Map<String, Long>> traceIdToTimestamp = getTraceIdsByServiceNames(request);
    List<String> annotationKeys = CassandraUtil.annotationKeys(request);
    ListenableFuture<Collection<String>> traceIds;
    if (annotationKeys.isEmpty()) {
      // Simplest case is when there is no annotation query. Limit is valid since there's no AND
      // query that could reduce the results returned to less than the limit.
      traceIds = Futures.transform(traceIdToTimestamp, CassandraUtil.traceIdsSortedByDescTimestamp());
    } else {
      // While a valid port of the scala cassandra span store (from zipkin 1.35), there is a fault.
      // each annotation key is an intersection, meaning we likely return < traceIndexFetchSize.
      List<ListenableFuture<Map<String, Long>>> futureKeySetsToIntersect = new ArrayList<>();
      if (request.spanName() != null) {
        futureKeySetsToIntersect.add(traceIdToTimestamp);
      }
      for (String annotationKey : annotationKeys) {
        futureKeySetsToIntersect
            .add(getTraceIdsByAnnotation(request, annotationKey, request.endTs(), traceIndexFetchSize));
      }
      // We achieve the AND goal, by intersecting each of the key sets.
      traceIds = Futures.transform(allAsList(futureKeySetsToIntersect), CassandraUtil.intersectKeySets());
      // @xxx the sorting by timestamp desc is broken here^
    }

    return new ListenableFutureCall<List<List<Span>>>() {
      @Override protected ListenableFuture<List<List<Span>>> newFuture() {
        return transformAsync(traceIds, new AsyncFunction<Collection<String>, List<List<Span>>>() {
          @Override
          public ListenableFuture<List<List<Span>>> apply(Collection<String> traceIds) {
            ImmutableSet<String> set =
              ImmutableSet.copyOf(Iterators.limit(traceIds.iterator(), request.limit()));

            return Futures.transform(
                    getSpansByTraceIds(set, maxTraceCols),
                    (List<Span> input) -> {

                List<List<Span>> traces = groupByTraceId(input, strictTraceId);
                // Due to tokenization of the trace ID, our matches are imprecise on Span.traceIdHigh
                for (Iterator<List<Span>> trace = traces.iterator(); trace.hasNext(); ) {
                  List<Span> next = trace.next();
                  if (next.get(0).traceId().length() > 16 && !request.test(next)) {
                    trace.remove();
                  }
                }
                return traces;
              });
          }

          @Override public String toString() {
            return "getSpansByTraceIds";
          }
        });
      }
    };
  }

  @Override public Call<List<Span>> getTrace(String traceId) {
    return new ListenableFutureCall<List<Span>>() {
      @Override protected ListenableFuture<List<Span>> newFuture() {
        return getSpansByTraceIds(Collections.singleton(traceId), maxTraceCols);
      }
    };
  }

  @Override public Call<List<String>> getServiceNames() {
    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectServiceNames, "select-service-names");

      return new ListenableFutureCall<List<String>>() {
        @Override protected ListenableFuture<List<String>> newFuture() {
          return transformAsync(
                  session.executeAsync(bound),
                  readResultAsOrderedSet(Collections.emptyList(), rowToServiceName));
        }
      };
    } catch (RuntimeException ex) {
      return FailedCall.create(ex);
    }
  }

  @Override public Call<List<String>> getSpanNames(String serviceName) {
    if (serviceName == null || serviceName.isEmpty()) return EMPTY_LIST;
    serviceName = checkNotNull(serviceName, "serviceName").toLowerCase();
    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectSpanNames, "select-span-names")
          .setString("service", serviceName)
          // no one is ever going to browse so many span names
          .setInt("limit_", 1000);

      return new ListenableFutureCall<List<String>>() {
        @Override protected ListenableFuture<List<String>> newFuture() {
          return transformAsync(
                  session.executeAsync(bound),
                  readResultAsOrderedSet(Collections.emptyList(), rowToSpanName));
        }
      };
    } catch (RuntimeException ex) {
      return FailedCall.create(ex);
    }
  }

  @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    List<Date> days = getDays(endTs, lookback);
    try {
      BoundStatement bound = CassandraUtil
              .bindWithName(selectDependencies, "select-dependencies")
              .setList("days", days);

      return new ListenableFutureCall<List<DependencyLink>>() {
        @Override protected ListenableFuture<List<DependencyLink>> newFuture() {
          return Futures.transform(
                  session.executeAsync(bound),
                  ConvertDependenciesResponse.INSTANCE);
        }
      };
    } catch (RuntimeException ex) {
      return FailedCall.create(ex);
    }
  }

  enum ConvertDependenciesResponse implements Function<ResultSet, List<DependencyLink>> {
    INSTANCE;

    @Override public List<DependencyLink> apply(@Nullable ResultSet rs) {
      ImmutableList.Builder<DependencyLink> unmerged = ImmutableList.builder();
      for (Row row : rs) {
        unmerged.add(
                DependencyLink.newBuilder()
                .parent(row.getString("parent"))
                .child(row.getString("child"))
                .errorCount(row.getLong("errors"))
                .callCount(row.getLong("calls"))
                .build());
      }
      return DependencyLinker.merge(unmerged.build());
    }
  }

  /**
   * Get the available trace information from the storage system. Spans in trace should be sorted by
   * the first annotation timestamp in that span. First event should be first in the spans list. <p>
   * The return list will contain only spans that have been found, thus the return list may not
   * match the provided list of ids.
   */
  ListenableFuture<List<Span>> getSpansByTraceIds(Set<String> traceIds, int limit) {
    checkNotNull(traceIds, "traceIds");
    if (traceIds.isEmpty()) {
      return immediateFuture(Collections.<Span>emptyList());
    }

    try {
      Statement bound = CassandraUtil.bindWithName(selectTraces, "select-traces")
          .setSet("trace_id", traceIds)
          .setInt("limit_", limit);

      return transformAsync(session.executeAsync(bound), readResults(Collections.emptyList(), rowToSpan));
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  ListenableFuture<Map<String, Long>> getTraceIdsByServiceNames(QueryRequest request) {
    long oldestData = Math.max(System.currentTimeMillis() - indexTtl * 1000, 0); // >= 1970
    long startTsMillis = Math.max((request.endTs() - request.lookback()), oldestData);
    long endTsMillis = Math.max(request.endTs(), oldestData);

    try {
      Set<String> serviceNames;
      if (null != request.serviceName()) {
        serviceNames = Collections.singleton(request.serviceName());
      } else {
        serviceNames = new LinkedHashSet<>(getServiceNames().execute());
        if (serviceNames.isEmpty()) {
          return immediateFuture(Collections.<String, Long>emptyMap());
        }
      }

      int startBucket = CassandraUtil.durationIndexBucket(startTsMillis * 1000);
      int endBucket = CassandraUtil.durationIndexBucket(endTsMillis * 1000);
      if (startBucket > endBucket) {
        throw new IllegalArgumentException(
            "Start bucket (" + startBucket + ") > end bucket (" + endBucket + ")");
      }
      Set<Integer> buckets = ContiguousSet.create(Range.closed(startBucket, endBucket), integers());
      boolean withDuration = null != request.minDuration() || null != request.maxDuration();
      List<ListenableFuture<Map<String, Long>>> futures = new ArrayList<>();

      if (200 < serviceNames.size() * buckets.size()) {
        LOG.warn("read against " + TABLE_TRACE_BY_SERVICE_SPAN
            + " fanning out to " + serviceNames.size() * buckets.size() + " requests");
        //@xxx the fan-out of requests here can be improved
      }

      for (String serviceName : serviceNames) {
        for (Integer bucket : buckets) {
          BoundStatement bound = CassandraUtil
              .bindWithName(
                  withDuration
                      ? selectTraceIdsByServiceSpanNameAndDuration
                      : selectTraceIdsByServiceSpanName,
                  "select-trace-ids-by-service-name")
              .setString("service", serviceName)
              .setString("span", null != request.spanName() ? request.spanName() : "")
              .setInt("bucket", bucket)
              .setUUID("start_ts", UUIDs.startOf(startTsMillis))
              .setUUID("end_ts", UUIDs.endOf(endTsMillis))
              .setInt("limit_", request.limit());

          if (withDuration) {
            bound = bound
                .setLong("start_duration", null != request.minDuration() ? request.minDuration() : 0)
                .setLong("end_duration", null != request.maxDuration() ? request.maxDuration() : Long.MAX_VALUE);
          }
          bound.setFetchSize(request.limit());

          futures.add(transformAsync(
                  session.executeAsync(bound),
                  readResultsAsMap(Collections.emptyMap(), traceIdToTimestamp)));
        }
      }

      return Futures.transform(allAsList(futures), collapseTraceIdMaps);
    } catch (IOException ex) {
      return immediateFailedFuture(ex);
    }
  }

  ListenableFuture<Map<String, Long>> getTraceIdsByAnnotation(
      QueryRequest request,
      String annotationKey,
      long endTsMillis,
      int limit) {

    long lookbackMillis = request.lookback();
    long oldestData = Math.max(System.currentTimeMillis() - indexTtl * 1000, 0); // >= 1970
    long startTsMillis = Math.max((endTsMillis - lookbackMillis), oldestData);
    endTsMillis = Math.max(endTsMillis, oldestData);

    try {
      BoundStatement bound =
          CassandraUtil.bindWithName(selectTraceIdsByAnnotation, "select-trace-ids-by-annotation")
              .setString("local_service", request.serviceName())
              .setString("annotation_query", "%" + annotationKey + "%")
              .setUUID("start_ts", UUIDs.startOf(startTsMillis))
              .setUUID("end_ts", UUIDs.endOf(endTsMillis))
              .setInt("limit_", limit);

      return transformAsync(session.executeAsync(bound), readResultsAsMap(Collections.emptyMap(), traceIdToLong));
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  private static <K, T> AsyncFunction<ResultSet, Map<K, T>> readResultsAsMap(
      Map<K, T> results,
      Function<Row, Map.Entry<K, T>> rowMapper) {

    return (rs) -> {
      if (!rs.isFullyFetched()) rs.fetchMoreResults();
      Map<K, T> newResults = new HashMap<>(results);
      for (Row row : rs) {
        Map.Entry<K, T> entry = rowMapper.apply(row);
        newResults.put(entry.getKey(), entry.getValue());
        if (2000 == rs.getAvailableWithoutFetching() && !rs.isFullyFetched()) rs.fetchMoreResults();
        if (0 == rs.getAvailableWithoutFetching()) break;
      }

      Map<K, T> finalResults = ImmutableMap.copyOf(newResults);
      return rs.getExecutionInfo().getPagingState() == null
            ? immediateFuture(finalResults)
            : transformAsync(rs.fetchMoreResults(), readResultsAsMap(finalResults, rowMapper));
    };
  }

  private static <T extends Comparable> AsyncFunction<ResultSet, List<T>> readResultAsOrderedSet(
      List<T> results,
      Function<Row, T> rowMapper) {

    return (rs) -> {
        if (!rs.isFullyFetched()) rs.fetchMoreResults();
        ImmutableSet.Builder<T> builder = ImmutableSet.<T>builder().addAll(results);
        for (Row row : rs) {
          builder.add(rowMapper.apply(row));
          if (2000 == rs.getAvailableWithoutFetching() && !rs.isFullyFetched()) rs.fetchMoreResults();
          if (0 == rs.getAvailableWithoutFetching()) break;
        }
        List<T> finalSet = ImmutableList.copyOf(Ordering.natural().sortedCopy(builder.build()));

        return rs.getExecutionInfo().getPagingState() == null
              ? immediateFuture(finalSet)
              : transformAsync(rs.fetchMoreResults(), readResultAsOrderedSet(finalSet, rowMapper));
    };
  }

  private static <T> AsyncFunction<ResultSet, List<T>> readResults(
      Iterable<T> results,
      Function<Row, T> rowMapper) {

    return (rs) -> {
        if (!rs.isFullyFetched()) rs.fetchMoreResults();
        ImmutableList.Builder<T> builder = ImmutableList.<T>builder().addAll(results);
        for (Row row : rs) {
          builder.add(rowMapper.apply(row));
          if (2000 == rs.getAvailableWithoutFetching() && !rs.isFullyFetched()) rs.fetchMoreResults();
          if (0 == rs.getAvailableWithoutFetching()) break;
        }
        List<T> finalResults = builder.build();

        return rs.getExecutionInfo().getPagingState() == null
              ? immediateFuture(finalResults)
              : transformAsync(rs.fetchMoreResults(), readResults(finalResults, rowMapper));
    };
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
        groupedByTraceId.put(traceId, new LinkedList<>());
      }
      groupedByTraceId.get(traceId).add(span);
    }
    return new ArrayList<>(groupedByTraceId.values());
  }

  private static class FailedCall<V> extends ListenableFutureCall<V> {

    private final RuntimeException ex;

    private FailedCall(RuntimeException ex) {
      this.ex = ex;
    }

    public static FailedCall create(RuntimeException ex) {
      return new FailedCall(ex);
    }

    @Override protected ListenableFuture<V> newFuture() {
      return immediateFailedFuture(ex);
    }
  }
}
