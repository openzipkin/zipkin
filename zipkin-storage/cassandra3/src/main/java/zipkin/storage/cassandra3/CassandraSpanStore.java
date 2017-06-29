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
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.DependencyLinker;
import zipkin.internal.GroupByTraceId;
import zipkin.internal.MergeById;
import zipkin.internal.Nullable;
import zipkin.storage.QueryRequest;
import zipkin.storage.cassandra3.Schema.AnnotationUDT;
import zipkin.storage.cassandra3.Schema.BinaryAnnotationUDT;
import zipkin.storage.cassandra3.Schema.TraceIdUDT;
import zipkin.storage.guava.GuavaSpanStore;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.DiscreteDomain.integers;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static zipkin.internal.Util.getDays;
import static zipkin.storage.cassandra3.Schema.TABLE_SERVICE_SPANS;
import static zipkin.storage.cassandra3.Schema.TABLE_TRACES;
import static zipkin.storage.cassandra3.Schema.TABLE_TRACE_BY_SERVICE_SPAN;

final class CassandraSpanStore implements GuavaSpanStore {
  private static final Logger LOG = LoggerFactory.getLogger(CassandraSpanStore.class);

  static final ListenableFuture<List<String>> EMPTY_LIST =
      immediateFuture(Collections.<String>emptyList());
  static final Function<List<Span>, List<Span>> OR_NULL = input -> input.isEmpty() ? null : input;

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
  private final Function<Row, Map.Entry<TraceIdUDT, Long>> traceIdToTimestamp;
  private final Function<Row, Map.Entry<TraceIdUDT, Long>> traceIdToLong;
  private final Function<Row, String> rowToSpanName;
  private final Function<Row, String> rowToServiceName;
  private final Function<Row, Span> rowToSpan;
  private final Function<List<Map<TraceIdUDT, Long>>, Map<TraceIdUDT, Long>> collapseTraceIdMaps;
  private final int traceTtl;
  private final int indexTtl;

  CassandraSpanStore(Session session, int maxTraceCols, int indexFetchMultiplier,
      boolean strictTraceId) {
    this.session = session;
    this.maxTraceCols = maxTraceCols;
    this.indexFetchMultiplier = indexFetchMultiplier;
    this.strictTraceId = strictTraceId;

    selectTraces = session.prepare(
        QueryBuilder.select("trace_id", "id", "ts", "span_name", "parent_id", "duration",
            "annotations", "binary_annotations")
            .from(TABLE_TRACES)
            .where(QueryBuilder.in("trace_id", QueryBuilder.bindMarker("trace_id")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectDependencies = session.prepare(
        QueryBuilder.select("links")
            .from(Schema.TABLE_DEPENDENCIES)
            .where(QueryBuilder.in("day", QueryBuilder.bindMarker("days"))));

    selectServiceNames = session.prepare(
        QueryBuilder.select("service_name")
            .distinct()
            .from(TABLE_SERVICE_SPANS));

    selectSpanNames = session.prepare(
        QueryBuilder.select("span_name")
            .from(TABLE_SERVICE_SPANS)
            .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectTraceIdsByServiceSpanName = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(TABLE_TRACE_BY_SERVICE_SPAN)
            .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
            .and(QueryBuilder.eq("span_name", QueryBuilder.bindMarker("span_name")))
            .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectTraceIdsByServiceSpanNameAndDuration = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(TABLE_TRACE_BY_SERVICE_SPAN)
            .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
            .and(QueryBuilder.eq("span_name", QueryBuilder.bindMarker("span_name")))
            .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .and(QueryBuilder.gte("duration", QueryBuilder.bindMarker("start_duration")))
            .and(QueryBuilder.lte("duration", QueryBuilder.bindMarker("end_duration")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectTraceIdsByAnnotation = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(TABLE_TRACES)
            .where(QueryBuilder.like("all_annotations", QueryBuilder.bindMarker("annotation")))
            .and(QueryBuilder.gte("ts_uuid", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts_uuid", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .allowFiltering());

    traceIdToTimestamp = row ->
      new AbstractMap.SimpleEntry<>(
          row.get("trace_id", TraceIdUDT.class),
          UUIDs.unixTimestamp(row.getUUID("ts")));

    traceIdToLong = row ->
        new AbstractMap.SimpleEntry<>(
            row.get("trace_id", TraceIdUDT.class),
            row.getLong("ts"));

    rowToSpanName = row -> row.getString("span_name");

    rowToServiceName = row -> row.getString("service_name");

    rowToSpan = row -> {
      TraceIdUDT traceId = row.get("trace_id", TraceIdUDT.class);
      Span.Builder builder = Span.builder()
          .traceIdHigh(traceId.getHigh())
          .traceId(traceId.getLow())
          .id(row.getLong("id"))
          .name(row.getString("span_name"))
          .duration(row.getLong("duration"));

      if (!row.isNull("ts")) {
        builder = builder.timestamp(row.getLong("ts"));
      }
      if (!row.isNull("duration")) {
        builder = builder.duration(row.getLong("duration"));
      }
      if (!row.isNull("parent_id")) {
        builder = builder.parentId(row.getLong("parent_id"));
      }
      for (AnnotationUDT udt : row.getList("annotations", AnnotationUDT.class)) {
        builder = builder.addAnnotation(udt.toAnnotation());
      }
      for (BinaryAnnotationUDT udt : row.getList("binary_annotations",
          BinaryAnnotationUDT.class)) {
        builder = builder.addBinaryAnnotation(udt.toBinaryAnnotation());
      }
      return builder.build();
    };

    collapseTraceIdMaps = input -> {
      Map<TraceIdUDT, Long> result = new LinkedHashMap<>();
      for (Map<TraceIdUDT, Long> m : input) {
        result.putAll(m);
      }
      return result;
    };

    KeyspaceMetadata md = Schema.getKeyspaceMetadata(session);
    this.traceTtl = md.getTable(TABLE_TRACES).getOptions().getDefaultTimeToLive();
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
  public ListenableFuture<List<List<Span>>> getTraces(final QueryRequest request) {
    // Over fetch on indexes as they don't return distinct (trace id, timestamp) rows.
    final int traceIndexFetchSize = request.limit * indexFetchMultiplier;
    ListenableFuture<Map<TraceIdUDT, Long>> traceIdToTimestamp = getTraceIdsByServiceNames(request);
    List<String> annotationKeys = CassandraUtil.annotationKeys(request);
    ListenableFuture<Collection<TraceIdUDT>> traceIds;
    if (annotationKeys.isEmpty()) {
      // Simplest case is when there is no annotation query. Limit is valid since there's no AND
      // query that could reduce the results returned to less than the limit.
      traceIds =
          Futures.transform(traceIdToTimestamp, CassandraUtil.traceIdsSortedByDescTimestamp());
    } else {
      // While a valid port of the scala cassandra span store (from zipkin 1.35), there is a fault.
      // each annotation key is an intersection, meaning we likely return < traceIndexFetchSize.
      List<ListenableFuture<Map<TraceIdUDT, Long>>> futureKeySetsToIntersect = new ArrayList<>();
      if (request.spanName != null) {
        futureKeySetsToIntersect.add(traceIdToTimestamp);
      }
      for (String annotationKey : annotationKeys) {
        futureKeySetsToIntersect
            .add(getTraceIdsByAnnotation(annotationKey, request.endTs, request.lookback, traceIndexFetchSize));
      }
      // We achieve the AND goal, by intersecting each of the key sets.
      traceIds =
          Futures.transform(allAsList(futureKeySetsToIntersect), CassandraUtil.intersectKeySets());
      // @xxx the sorting by timestamp desc is broken here^
    }
    return transform(traceIds, new AsyncFunction<Collection<TraceIdUDT>, List<List<Span>>>() {
      @Override public ListenableFuture<List<List<Span>>> apply(Collection<TraceIdUDT> traceIds) {
        ImmutableSet<TraceIdUDT> set =
            ImmutableSet.copyOf(Iterators.limit(traceIds.iterator(), request.limit));
        return transform(getSpansByTraceIds(set, maxTraceCols),
            new Function<List<Span>, List<List<Span>>>() {
              @Override public List<List<Span>> apply(List<Span> input) {
                return GroupByTraceId.apply(input, strictTraceId, true);
              }
            });
      }

      @Override public String toString() {
        return "getSpansByTraceIds";
      }
    });
  }

  @Override public ListenableFuture<List<Span>> getRawTrace(long traceId) {
    return getRawTrace(0L, traceId);
  }

  @Override public ListenableFuture<List<Span>> getRawTrace(long traceIdHigh, long traceIdLow) {
    TraceIdUDT traceIdUDT = new TraceIdUDT(strictTraceId ? 0L : traceIdHigh, traceIdLow);
    return transform(getSpansByTraceIds(Collections.singleton(traceIdUDT), maxTraceCols), OR_NULL);
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceId) {
    return getTrace(0L, traceId);
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceIdHigh, long traceIdLow) {
    return transform(getRawTrace(traceIdHigh, traceIdLow), AdjustTrace.INSTANCE);
  }

  enum AdjustTrace implements Function<Collection<Span>, List<Span>> {
    INSTANCE;

    @Override public List<Span> apply(Collection<Span> input) {
      List<Span> result = CorrectForClockSkew.apply(MergeById.apply(input));
      return result.isEmpty() ? null : result;
    }
  }

  @Override public ListenableFuture<List<String>> getServiceNames() {
    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectServiceNames, "select-service-names");
      return transform(session.executeAsync(bound), readResultAsOrderedSet(Collections.emptyList(), rowToServiceName));
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  @Override public ListenableFuture<List<String>> getSpanNames(String serviceName) {
    if (serviceName == null || serviceName.isEmpty()) return EMPTY_LIST;
    serviceName = checkNotNull(serviceName, "serviceName").toLowerCase();
    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectSpanNames, "select-span-names")
          .setString("service_name", serviceName)
          // no one is ever going to browse so many span names
          .setInt("limit_", 1000);

      return transform(session.executeAsync(bound), readResultAsOrderedSet(Collections.emptyList(), rowToSpanName));
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  @Override public ListenableFuture<List<DependencyLink>> getDependencies(long endTs,
      @Nullable Long lookback) {
    List<Date> days = getDays(endTs, lookback);
    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectDependencies, "select-dependencies")
          .setList("days", days);
      return transform(session.executeAsync(bound), ConvertDependenciesResponse.INSTANCE);
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  enum ConvertDependenciesResponse implements Function<ResultSet, List<DependencyLink>> {
    INSTANCE;

    @Override public List<DependencyLink> apply(ResultSet rs) {
      ImmutableList.Builder<DependencyLink> unmerged = ImmutableList.builder();
      for (Row row : rs) {
        ByteBuffer encodedDayOfDependencies = row.getBytes("links");
        for (DependencyLink link : Codec.THRIFT.readDependencyLinks(encodedDayOfDependencies)) {
          unmerged.add(link);
        }
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
  ListenableFuture<List<Span>> getSpansByTraceIds(Set<TraceIdUDT> traceIds, int limit) {
    checkNotNull(traceIds, "traceIds");
    if (traceIds.isEmpty()) {
      return immediateFuture(Collections.<Span>emptyList());
    }

    try {
      Statement bound = CassandraUtil.bindWithName(selectTraces, "select-traces")
          .setSet("trace_id", traceIds)
          .setInt("limit_", limit);

      return transform(session.executeAsync(bound), readResults(Collections.emptyList(), rowToSpan));
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  ListenableFuture<Map<TraceIdUDT, Long>> getTraceIdsByServiceNames(QueryRequest request) {
    long oldestData = Math.max(System.currentTimeMillis() - indexTtl * 1000, 0); // >= 1970
    long startTsMillis = Math.max((request.endTs - request.lookback), oldestData);
    long endTsMillis = Math.max(request.endTs, oldestData);

    try {
      Set<String> serviceNames;
      if (null != request.serviceName) {
        serviceNames = Collections.singleton(request.serviceName);
      } else {
        serviceNames = new LinkedHashSet<>(getServiceNames().get());
        if (serviceNames.isEmpty()) {
          return immediateFuture(Collections.<TraceIdUDT, Long>emptyMap());
        }
      }

      int startBucket = CassandraUtil.durationIndexBucket(startTsMillis * 1000);
      int endBucket = CassandraUtil.durationIndexBucket(endTsMillis * 1000);
      if (startBucket > endBucket) {
        throw new IllegalArgumentException(
            "Start bucket (" + startBucket + ") > end bucket (" + endBucket + ")");
      }
      Set<Integer> buckets = ContiguousSet.create(Range.closed(startBucket, endBucket), integers());
      boolean withDuration = null != request.minDuration || null != request.maxDuration;
      List<ListenableFuture<Map<TraceIdUDT, Long>>> futures = new ArrayList<>();

      if (200 < serviceNames.size() * buckets.size()) {
        LOG.warn("read against " + TABLE_TRACE_BY_SERVICE_SPAN
            + " fanning out to " + serviceNames.size() * buckets.size() + " requests");
        //@xxx the fan-out of requests here can be improved
      }

      for (String serviceName : serviceNames) {
        for (Integer bucket : buckets) {
          BoundStatement bound = CassandraUtil
              .bindWithName(
                  withDuration ? selectTraceIdsByServiceSpanNameAndDuration
                      : selectTraceIdsByServiceSpanName,
                  "select-trace-ids-by-service-name")
              .setString("service_name", serviceName)
              .setString("span_name", null != request.spanName ? request.spanName : "")
              .setInt("bucket", bucket)
              .setUUID("start_ts", UUIDs.startOf(startTsMillis))
              .setUUID("end_ts", UUIDs.endOf(endTsMillis))
              .setInt("limit_", request.limit);

          if (withDuration) {
            bound = bound
                .setLong("start_duration", null != request.minDuration ? request.minDuration : 0)
                .setLong("end_duration",
                    null != request.maxDuration ? request.maxDuration : Long.MAX_VALUE);
          }
          bound.setFetchSize(request.limit);
          futures.add(transform(session.executeAsync(bound), readResultsAsMap(Collections.emptyMap(), traceIdToTimestamp)));
        }
      }

      return transform(allAsList(futures), collapseTraceIdMaps);
    } catch (RuntimeException | InterruptedException | ExecutionException ex) {
      return immediateFailedFuture(ex);
    }
  }

  ListenableFuture<Map<TraceIdUDT, Long>> getTraceIdsByAnnotation(
      String annotationKey,
      long endTsMillis,
      long lookbackMillis,
      int limit) {
    long oldestData = Math.max(System.currentTimeMillis() - indexTtl * 1000, 0); // >= 1970
    long startTsMillis = Math.max((endTsMillis - lookbackMillis), oldestData);
    endTsMillis = Math.max(endTsMillis, oldestData);

    try {
      BoundStatement bound =
          CassandraUtil.bindWithName(selectTraceIdsByAnnotation, "select-trace-ids-by-annotation")
              .setString("annotation", "%" + annotationKey + "%")
              .setUUID("start_ts", UUIDs.startOf(startTsMillis))
              .setUUID("end_ts", UUIDs.endOf(endTsMillis))
              .setInt("limit_", limit);

      return transform(session.executeAsync(bound), readResultsAsMap(Collections.emptyMap(), traceIdToLong));
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  private static <K, T> AsyncFunction<ResultSet, Map<K, T>> readResultsAsMap(final Map<K, T> results,
      Function<Row, Map.Entry<K, T>> rowMapper) {
    return new AsyncFunction<ResultSet, Map<K, T>>() {
      @Override public ListenableFuture<Map<K, T>> apply(ResultSet rs) throws Exception {
        // How far we can go without triggering the blocking fetch:
        int remainingInPage = rs.getAvailableWithoutFetching();

        Map<K, T> newResults = new HashMap<>(results);

        for (Row row : rs) {
          Map.Entry<K, T> entry = rowMapper.apply(row);
          newResults.put(entry.getKey(), entry.getValue());
          if (--remainingInPage == 0) break;
        }

        boolean wasLastPage = rs.getExecutionInfo().getPagingState() == null;

        Map<K, T> finalResults = ImmutableMap.copyOf(newResults);

        if (wasLastPage) {
          return Futures.immediateFuture(finalResults);
        } else {
          ListenableFuture<ResultSet> future = rs.fetchMoreResults();
          return Futures.transform(future, readResultsAsMap(finalResults, rowMapper));
        }
      }
    };
  }

  private static <T extends Comparable> AsyncFunction<ResultSet, List<T>> readResultAsOrderedSet(
      final List<T> results, Function<Row, T> rowMapper) {
    return new AsyncFunction<ResultSet, List<T>>() {
      @Override
      public ListenableFuture<List<T>> apply(ResultSet rs) throws Exception {

        // How far we can go without triggering the blocking fetch:
        int remainingInPage = rs.getAvailableWithoutFetching();

        ImmutableSet.Builder<T> builder = ImmutableSet.builder();
        builder.addAll(results);

        for (Row row : rs) {
          builder.add(rowMapper.apply(row));
          if (--remainingInPage == 0) break;
        }

        boolean wasLastPage = rs.getExecutionInfo().getPagingState() == null;

        List<T> orderedResults = Ordering.natural().sortedCopy(builder.build());
        List<T> finalSet = ImmutableList.copyOf(orderedResults);

        if (wasLastPage) {
          return Futures.immediateFuture(finalSet);
        } else {
          ListenableFuture<ResultSet> future = rs.fetchMoreResults();
          return Futures.transform(future, readResultAsOrderedSet(finalSet, rowMapper));
        }
      }
    };
  }

  private static <T> AsyncFunction<ResultSet, List<T>> readResults(final List<T> results,
      Function<Row, T> rowMapper) {
    return new AsyncFunction<ResultSet, List<T>>() {
      @Override
      public ListenableFuture<List<T>> apply(ResultSet rs) throws Exception {

        // How far we can go without triggering the blocking fetch:
        int remainingInPage = rs.getAvailableWithoutFetching();

        ImmutableList.Builder<T> builder = ImmutableList.builder();
        builder.addAll(results);

        for (Row row : rs) {
          builder.add(rowMapper.apply(row));
          if (--remainingInPage == 0) break;
        }

        boolean wasLastPage = rs.getExecutionInfo().getPagingState() == null;

        List<T> finalResults = builder.build();

        if (wasLastPage) {
          return Futures.immediateFuture(finalResults);
        } else {
          ListenableFuture<ResultSet> future = rs.fetchMoreResults();
          return Futures.transform(future, readResults(finalResults, rowMapper));
        }
      }
    };
  }
}
