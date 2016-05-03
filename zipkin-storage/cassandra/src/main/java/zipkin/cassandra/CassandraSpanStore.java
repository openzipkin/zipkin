/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Function;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.Dependencies;
import zipkin.internal.MergeById;
import zipkin.internal.Nullable;
import zipkin.internal.Pair;
import zipkin.spanstore.guava.GuavaSpanStore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.DiscreteDomain.integers;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static zipkin.cassandra.CassandraUtil.annotationKeys;
import static zipkin.cassandra.CassandraUtil.durationIndexBucket;
import static zipkin.cassandra.CassandraUtil.intersectKeySets;
import static zipkin.cassandra.CassandraUtil.iso8601;
import static zipkin.cassandra.CassandraUtil.keyset;
import static zipkin.internal.Util.midnightUTC;

final class CassandraSpanStore implements GuavaSpanStore {
  private static final Logger LOG = LoggerFactory.getLogger(CassandraSpanStore.class);

  static final ListenableFuture<List<String>> EMPTY_LIST =
      immediateFuture(Collections.<String>emptyList());
  static final Ordering<List<Span>> TRACE_DESCENDING = Ordering.from(new Comparator<List<Span>>() {
    @Override
    public int compare(List<Span> left, List<Span> right) {
      return right.get(0).compareTo(left.get(0));
    }
  });

  private final int indexTtl;
  private final int maxTraceCols;
  private final Session session;
  private final TimestampCodec timestampCodec;
  private final Set<Integer> buckets;
  private final PreparedStatement selectTraces;
  private final PreparedStatement selectDependencies;
  private final PreparedStatement selectServiceNames;
  private final PreparedStatement selectSpanNames;
  private final PreparedStatement selectTraceIdsByServiceName;
  private final PreparedStatement selectTraceIdsBySpanName;
  private final PreparedStatement selectTraceIdsByAnnotations;
  private final PreparedStatement selectTraceIdsBySpanDuration;

  CassandraSpanStore(Session session, int bucketCount, int indexTtl, int maxTraceCols) {
    this.session = session;
    this.indexTtl = indexTtl;
    this.maxTraceCols = maxTraceCols;
    this.timestampCodec = new TimestampCodec(session);
    this.buckets = ContiguousSet.create(Range.closedOpen(0, bucketCount), integers());

    selectTraces = session.prepare(
        QueryBuilder.select("trace_id", "span")
            .from("traces")
            .where(QueryBuilder.in("trace_id", QueryBuilder.bindMarker("trace_id")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectDependencies = session.prepare(
        QueryBuilder.select("dependencies")
            .from("dependencies")
            .where(QueryBuilder.in("day", QueryBuilder.bindMarker("days"))));

    selectServiceNames = session.prepare(
        QueryBuilder.select("service_name")
            .from("service_names"));

    selectSpanNames = session.prepare(
        QueryBuilder.select("span_name")
            .from("span_names")
            .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
            .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectTraceIdsByServiceName = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from("service_name_index")
            .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
            .and(QueryBuilder.in("bucket", QueryBuilder.bindMarker("bucket")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .orderBy(QueryBuilder.desc("ts")));

    selectTraceIdsBySpanName = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from("service_span_name_index")
            .where(
                QueryBuilder.eq("service_span_name", QueryBuilder.bindMarker("service_span_name")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .orderBy(QueryBuilder.desc("ts")));

    selectTraceIdsByAnnotations = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from("annotations_index")
            .where(QueryBuilder.eq("annotation", QueryBuilder.bindMarker("annotation")))
            .and(QueryBuilder.in("bucket", QueryBuilder.bindMarker("bucket")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .orderBy(QueryBuilder.desc("ts")));

    selectTraceIdsBySpanDuration = session.prepare(
        QueryBuilder.select("duration", "ts", "trace_id")
            .from("span_duration_index")
            .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
            .and(QueryBuilder.eq("span_name", QueryBuilder.bindMarker("span_name")))
            .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("time_bucket")))
            .and(QueryBuilder.lte("duration", QueryBuilder.bindMarker("max_duration")))
            .and(QueryBuilder.gte("duration", QueryBuilder.bindMarker("min_duration")))
            .orderBy(QueryBuilder.desc("duration")));
  }

  @Override
  public ListenableFuture<List<List<Span>>> getTraces(QueryRequest request) {
    String spanName = spanName(request.spanName);
    ListenableFuture<Map<Long, Long>> traceIdToTimestamp;
    if (request.minDuration != null || request.maxDuration != null) {
      traceIdToTimestamp = getTraceIdsByDuration(request);
    } else if (!spanName.isEmpty()) {
      traceIdToTimestamp = getTraceIdsBySpanName(request.serviceName, spanName,
          request.endTs * 1000, request.lookback * 1000, request.limit);
    } else {
      traceIdToTimestamp = getTraceIdsByServiceName(request.serviceName,
          request.endTs * 1000, request.lookback * 1000, request.limit);
    }

    List<String> annotationKeys = annotationKeys(request);

    ListenableFuture<Set<Long>> traceIds;
    if (annotationKeys.isEmpty()) {
      // Simplest case is when there is no annotation query. Limit is valid since there's no AND
      // query that could reduce the results returned to less than the limit.
      traceIds = transform(traceIdToTimestamp, keyset());
    } else {
      // While a valid port of the scala cassandra span store (from zipkin 1.35), there is a fault.
      // each annotation key is an intersection, which means we are likely to return < limit.
      List<ListenableFuture<Map<Long, Long>>> futureKeySetsToIntersect = new ArrayList<>();
      futureKeySetsToIntersect.add(traceIdToTimestamp);
      for (String annotationKey : annotationKeys) {
        futureKeySetsToIntersect.add(getTraceIdsByAnnotation(annotationKey,
            request.endTs * 1000, request.lookback * 1000, request.limit));
      }
      // We achieve the AND goal, by intersecting each of the key sets.
      traceIds = transform(allAsList(futureKeySetsToIntersect), intersectKeySets());
    }
    return transform(traceIds, new AsyncFunction<Set<Long>, List<List<Span>>>() {
      @Override public ListenableFuture<List<List<Span>>> apply(Set<Long> traceIds) {
        return transform(getSpansByTraceIds(traceIds.toArray(new Long[traceIds.size()]),
            maxTraceCols), ConvertTracesResponse.INSTANCE);
      }

      @Override public String toString() {
        return "getSpansByTraceIds";
      }
    });
  }

  static String spanName(String nullableSpanName) {
    return nullableSpanName != null ? nullableSpanName : "";
  }

  enum ConvertTracesResponse implements Function<Map<Long, List<Span>>, List<List<Span>>> {
    INSTANCE;

    @Override public List<List<Span>> apply(Map<Long, List<Span>> input) {
      Collection<List<Span>> unmerged = input.values();

      List<List<Span>> result = new ArrayList<>(unmerged.size());
      for (List<Span> spans : unmerged) {
        result.add(CorrectForClockSkew.apply(MergeById.apply(spans)));
      }
      return TRACE_DESCENDING.immutableSortedCopy(result);
    }
  }

  @Override public ListenableFuture<List<Span>> getRawTrace(long traceId) {
    return transform(getSpansByTraceIds(new Long[] {traceId}, maxTraceCols),
        new Function<Map<Long, List<Span>>, List<Span>>() {
          @Override public List<Span> apply(Map<Long, List<Span>> encodedTraces) {
            if (encodedTraces.isEmpty()) return null;
            return encodedTraces.values().iterator().next();
          }
        });
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceId) {
    return transform(getRawTrace(traceId), new Function<List<Span>, List<Span>>() {
      @Override public List<Span> apply(List<Span> input) {
        if (input == null || input.isEmpty()) return null;
        return ImmutableList.copyOf(CorrectForClockSkew.apply(MergeById.apply(input)));
      }
    });
  }

  @Override public ListenableFuture<List<String>> getServiceNames() {
    try {
      BoundStatement bound = selectServiceNames.bind();
      if (LOG.isDebugEnabled()) {
        LOG.debug(selectServiceNames.getQueryString());
      }

      return transform(session.executeAsync(bound), new Function<ResultSet, List<String>>() {
            @Override public List<String> apply(ResultSet input) {
              Set<String> serviceNames = new HashSet<>();
              for (Row row : input) {
                serviceNames.add(row.getString("service_name"));
              }
              return Ordering.natural().sortedCopy(serviceNames);
            }
          }
      );
    } catch (RuntimeException ex) {
      LOG.error("failed " + selectServiceNames.getQueryString(), ex);
      return Futures.immediateFailedFuture(ex);
    }
  }

  @Override public ListenableFuture<List<String>> getSpanNames(String serviceName) {
    if (serviceName == null || serviceName.isEmpty()) return EMPTY_LIST;
    serviceName = checkNotNull(serviceName, "serviceName").toLowerCase();
    int bucket = 0;
    try {
      BoundStatement bound = selectSpanNames.bind()
          .setString("service_name", serviceName)
          .setInt("bucket", bucket)
          // no one is ever going to browse so many span names
          .setInt("limit_", 1000);

      if (LOG.isDebugEnabled()) {
        LOG.debug(debugSelectSpanNames(bucket, serviceName));
      }

      return transform(session.executeAsync(bound), new Function<ResultSet, List<String>>() {
            @Override public List<String> apply(ResultSet input) {
              Set<String> spanNames = new HashSet<>();
              for (Row row : input) {
                spanNames.add(row.getString("span_name"));
              }
              return Ordering.natural().sortedCopy(spanNames);
            }
          }
      );
    } catch (RuntimeException ex) {
      LOG.error("failed " + debugSelectSpanNames(bucket, serviceName), ex);
      throw ex;
    }
  }

  @Override public ListenableFuture<List<DependencyLink>> getDependencies(long endTs,
      @Nullable Long lookback) {
    long endEpochDayMillis = midnightUTC(endTs);
    long startEpochDayMillis = midnightUTC(endTs - (lookback != null ? lookback : endTs));

    List<Date> days = getDays(startEpochDayMillis, endEpochDayMillis);
    try {
      BoundStatement bound = selectDependencies.bind().setList("days", days);
      if (LOG.isDebugEnabled()) {
        LOG.debug(debugSelectDependencies(days));
      }
      return transform(session.executeAsync(bound), ConvertDependenciesResponse.INSTANCE);
    } catch (RuntimeException ex) {
      LOG.error("failed " + debugSelectDependencies(days), ex);
      return Futures.immediateFailedFuture(ex);
    }
  }

  enum ConvertDependenciesResponse implements Function<ResultSet, List<DependencyLink>> {
    INSTANCE;

    @Override public List<DependencyLink> apply(ResultSet rs) {
      // Combine the dependency links from startEpochDayMillis until endEpochDayMillis
      Map<Pair<String>, Long> links = new LinkedHashMap<>();

      for (Row row : rs) {
        ByteBuffer encodedDayOfDependencies = row.getBytes("dependencies");
        for (DependencyLink link : Dependencies.fromThrift(encodedDayOfDependencies).links) {
          Pair<String> parentChild = Pair.create(link.parent, link.child);
          long callCount = links.containsKey(parentChild) ? links.get(parentChild) : 0L;
          callCount += link.callCount;
          links.put(parentChild, callCount);
        }
      }

      List<DependencyLink> result = new ArrayList<>(links.size());
      for (Map.Entry<Pair<String>, Long> link : links.entrySet()) {
        result.add(DependencyLink.create(link.getKey()._1, link.getKey()._2, link.getValue()));
      }
      return result;
    }
  }

  /**
   * Get the available trace information from the storage system. Spans in trace should be sorted by
   * the first annotation timestamp in that span. First event should be first in the spans list. <p>
   * The return list will contain only spans that have been found, thus the return list may not
   * match the provided list of ids.
   */
  ListenableFuture<Map<Long, List<Span>>> getSpansByTraceIds(Long[] traceIds, int limit) {
    checkNotNull(traceIds, "traceIds");
    if (traceIds.length == 0) {
      return Futures.immediateFuture(Collections.<Long, List<Span>>emptyMap());
    }

    try {
      BoundStatement bound = selectTraces.bind()
          .setList("trace_id", Arrays.asList(traceIds))
          .setInt("limit_", limit);

      bound.setFetchSize(Integer.MAX_VALUE);

      if (LOG.isDebugEnabled()) {
        LOG.debug(debugSelectTraces(traceIds, limit));
      }

      return transform(session.executeAsync(bound),
          new Function<ResultSet, Map<Long, List<Span>>>() {
            @Override public Map<Long, List<Span>> apply(ResultSet input) {
              Map<Long, List<Span>> spans = new LinkedHashMap<>();

              for (Row row : input) {
                long traceId = row.getLong("trace_id");
                if (!spans.containsKey(traceId)) {
                  spans.put(traceId, new ArrayList<Span>());
                }
                spans.get(traceId).add(Codec.THRIFT.readSpan(row.getBytes("span")));
              }

              return spans;
            }
          }
      );
    } catch (RuntimeException ex) {
      LOG.error("failed " + debugSelectTraces(traceIds, limit), ex);
      return Futures.immediateFailedFuture(ex);
    }
  }

  private String debugSelectTraces(Long[] traceIds, int limit) {
    return selectTraces.getQueryString()
        .replace(":trace_id", Arrays.toString(traceIds))
        .replace(":limit_", String.valueOf(limit));
  }

  private String debugSelectDependencies(List<Date> days) {
    StringBuilder dates = new StringBuilder(iso8601(days.get(0).getTime() * 1000));
    if (days.size() > 1) {
      dates.append(" until ").append(iso8601(days.get(days.size() - 1).getTime() * 1000));
    }
    return selectDependencies.getQueryString().replace(":days", dates.toString());
  }

  private String debugSelectSpanNames(int bucket, String serviceName) {
    return selectSpanNames.getQueryString()
        .replace(":bucket", String.valueOf(bucket))
        .replace(":service_name", serviceName);
  }

  ListenableFuture<Map<Long, Long>> getTraceIdsByServiceName(String serviceName, long endTs,
      long lookback, int limit) {
    checkNotNull(serviceName, "serviceName");
    checkArgument(!serviceName.isEmpty(), "serviceName");
    long startTs = endTs - lookback;
    try {
      BoundStatement bound = selectTraceIdsByServiceName.bind()
          .setString("service_name", serviceName)
          .setSet("bucket", buckets)
          .setBytesUnsafe("start_ts", timestampCodec.serialize(startTs))
          .setBytesUnsafe("end_ts", timestampCodec.serialize(endTs))
          .setInt("limit_", limit);

      bound.setFetchSize(Integer.MAX_VALUE);

      if (LOG.isDebugEnabled()) {
        LOG.debug(debugSelectTraceIdsByServiceName(serviceName, buckets, startTs, endTs, limit));
      }

      return transform(session.executeAsync(bound), new Function<ResultSet, Map<Long, Long>>() {
            @Override public Map<Long, Long> apply(ResultSet input) {
              Map<Long, Long> traceIdsToTimestamps = new LinkedHashMap<>();
              for (Row row : input) {
                traceIdsToTimestamps.put(row.getLong("trace_id"),
                    timestampCodec.deserialize(row, "ts"));
              }
              return traceIdsToTimestamps;
            }
          }
      );
    } catch (RuntimeException ex) {
      LOG.error(
          "failed " + debugSelectTraceIdsByServiceName(serviceName, buckets, startTs, endTs, limit),
          ex);
      return Futures.immediateFailedFuture(ex);
    }
  }

  private String debugSelectTraceIdsByServiceName(String serviceName, Set<Integer> buckets,
      long startTs, long endTs, int limit) {
    return selectTraceIdsByServiceName.getQueryString()
        .replace(":service_name", serviceName)
        .replace(":bucket", buckets.toString())
        .replace(":start_ts", iso8601(startTs))
        .replace(":end_ts", iso8601(endTs))
        .replace(":limit_", String.valueOf(limit));
  }

  ListenableFuture<Map<Long, Long>> getTraceIdsBySpanName(String serviceName,
      String spanName, long endTs, long lookback, int limit) {
    checkNotNull(serviceName, "serviceName");
    checkArgument(!serviceName.isEmpty(), "serviceName");
    checkArgument(!spanName.isEmpty(), "spanName");
    String serviceSpanName = serviceName + "." + spanName;
    long startTs = endTs - lookback;
    try {
      BoundStatement bound = selectTraceIdsBySpanName.bind()
          .setString("service_span_name", serviceSpanName)
          .setBytesUnsafe("start_ts", timestampCodec.serialize(startTs))
          .setBytesUnsafe("end_ts", timestampCodec.serialize(endTs))
          .setInt("limit_", limit);

      if (LOG.isDebugEnabled()) {
        LOG.debug(debugSelectTraceIdsBySpanName(serviceSpanName, startTs, endTs, limit));
      }

      return transform(session.executeAsync(bound), new Function<ResultSet, Map<Long, Long>>() {
            @Override public Map<Long, Long> apply(ResultSet input) {
              Map<Long, Long> traceIdsToTimestamps = new LinkedHashMap<>();
              for (Row row : input) {
                traceIdsToTimestamps.put(row.getLong("trace_id"),
                    timestampCodec.deserialize(row, "ts"));
              }
              return traceIdsToTimestamps;
            }
          }
      );
    } catch (RuntimeException ex) {
      LOG.error("failed " + debugSelectTraceIdsBySpanName(serviceSpanName, startTs, endTs, limit),
          ex);
      return Futures.immediateFailedFuture(ex);
    }
  }

  private String debugSelectTraceIdsBySpanName(String serviceSpanName, long startTs, long endTs,
      int limit) {
    return selectTraceIdsBySpanName.getQueryString()
        .replace(":service_span_name", serviceSpanName)
        .replace(":start_ts", iso8601(startTs))
        .replace(":end_ts", iso8601(endTs))
        .replace(":limit_", String.valueOf(limit));
  }

  ListenableFuture<Map<Long, Long>> getTraceIdsByAnnotation(String annotationKey,
      long endTs, long lookback, int limit) {
    long startTs = endTs - lookback;
    try {
      BoundStatement bound = selectTraceIdsByAnnotations.bind()
          .setBytes("annotation", CassandraUtil.toByteBuffer(annotationKey))
          .setSet("bucket", buckets)
          .setBytesUnsafe("start_ts", timestampCodec.serialize(startTs))
          .setBytesUnsafe("end_ts", timestampCodec.serialize(endTs))
          .setInt("limit_", limit);

      bound.setFetchSize(Integer.MAX_VALUE);

      if (LOG.isDebugEnabled()) {
        LOG.debug(debugSelectTraceIdsByAnnotations(annotationKey, buckets, startTs, endTs, limit));
      }

      return transform(session.executeAsync(bound), new Function<ResultSet, Map<Long, Long>>() {
            @Override public Map<Long, Long> apply(ResultSet input) {
              Map<Long, Long> traceIdsToTimestamps = new LinkedHashMap<>();
              for (Row row : input) {
                traceIdsToTimestamps.put(row.getLong("trace_id"),
                    timestampCodec.deserialize(row, "ts"));
              }
              return traceIdsToTimestamps;
            }
          }
      );
    } catch (CharacterCodingException | RuntimeException ex) {
      LOG.error("failed " + debugSelectTraceIdsByAnnotations(annotationKey, buckets, startTs, endTs,
          limit),
          ex);
      return Futures.immediateFailedCheckedFuture(ex);
    }
  }

  private String debugSelectTraceIdsByAnnotations(String annotationKey, Set<Integer> buckets,
      long startTs, long endTs, int limit) {
    return selectTraceIdsByAnnotations.getQueryString()
        .replace(":annotation", annotationKey)
        .replace(":bucket", buckets.toString())
        .replace(":start_ts", iso8601(startTs))
        .replace(":end_ts", iso8601(endTs))
        .replace(":limit_", String.valueOf(limit));
  }

  /** Returns a map of trace id to timestamp (in microseconds) */
  ListenableFuture<Map<Long, Long>> getTraceIdsByDuration(QueryRequest request) {
    long oldestData = (System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(indexTtl)) * 1000;

    long startTs = Math.max((request.endTs - request.lookback) * 1000, oldestData);
    long endTs = Math.max(request.endTs * 1000, oldestData);

    int startBucket = durationIndexBucket(startTs);
    int endBucket = durationIndexBucket(endTs);
    if (startBucket > endBucket) {
      throw new IllegalArgumentException(
          "Start bucket (" + startBucket + ") > end bucket (" + endBucket + ")");
    }

    List<ListenableFuture<List<DurationRow>>> futures = new ArrayList<>();
    for (int i = startBucket; i <= endBucket; i++) { // range closed
      futures.add(oneBucketDurationQuery(request, i, startTs, endTs));
    }

    return transform(Futures.successfulAsList(futures),
        new Function<List<List<DurationRow>>, Map<Long, Long>>() {
          @Override public Map<Long, Long> apply(List<List<DurationRow>> input) {
            // find earliest startTs for each trace ID
            Map<Long, Long> result = new LinkedHashMap<>();
            for (DurationRow row : Iterables.concat(input)) {
              Long oldValue = result.get(row.trace_id);
              if (oldValue == null || oldValue > row.timestamp) {
                result.put(row.trace_id, row.timestamp);
              }
            }
            return Collections.unmodifiableMap(result);
          }
        });
  }

  ListenableFuture<List<DurationRow>> oneBucketDurationQuery(QueryRequest request, int bucket,
      final long startTs, final long endTs) {
    String serviceName = request.serviceName;
    String spanName = spanName(request.spanName);
    long minDuration = request.minDuration;
    long maxDuration = request.maxDuration != null ? request.maxDuration : Long.MAX_VALUE;
    int limit = request.limit;
    BoundStatement bound = selectTraceIdsBySpanDuration.bind()
        .setInt("time_bucket", bucket)
        .setString("service_name", serviceName)
        .setString("span_name", spanName)
        .setLong("min_duration", minDuration)
        .setLong("max_duration", maxDuration);

    // optimistically setting fetch size to 'limit' here. Since we are likely to filter some results
    // because their timestamps are out of range, we may need to fetch again.
    // TODO figure out better strategy
    bound.setFetchSize(limit);
    if (LOG.isDebugEnabled()) {
      LOG.debug(debugSelectTraceIdsByDuration(
          bucket,
          request.serviceName,
          request.spanName,
          request.minDuration,
          request.maxDuration,
          request.limit
      ));
    }
    return transform(session.executeAsync(bound), new Function<ResultSet, List<DurationRow>>() {
      @Override public List<DurationRow> apply(ResultSet rs) {
        ImmutableList.Builder<DurationRow> result = ImmutableList.builder();
        for (Row input : rs) {
          DurationRow row = new DurationRow(input);
          if (row.timestamp >= startTs && row.timestamp <= endTs) {
            result.add(row);
          }
        }
        return result.build();
      }
    });
  }

  private String debugSelectTraceIdsByDuration(int bucket, String serviceName, String spanName,
      long minDuration, long maxDuration, int limit) {
    return selectTraceIdsBySpanDuration.getQueryString()
        .replace(":time_bucket", String.valueOf(bucket))
        .replace(":service_name", serviceName)
        .replace(":span_name", spanName)
        .replace(":min_duration", String.valueOf(minDuration))
        .replace(":max_duration", String.valueOf(maxDuration))
        .replace(":limit_", String.valueOf(limit));
  }

  class DurationRow {
    Long trace_id;
    Long duration;
    Long timestamp; // inflated back to microseconds

    DurationRow(Row row) {
      trace_id = row.getLong("trace_id");
      duration = row.getLong("duration");
      timestamp = timestampCodec.deserialize(row, "ts");
    }

    @Override public String toString() {
      return String.format("trace_id=%d, duration=%d, timestamp=%d", trace_id, duration, timestamp);
    }
  }

  private static List<Date> getDays(long from, long to) {
    List<Date> days = new ArrayList<>();
    for (long time = from; time <= to; time += TimeUnit.DAYS.toMillis(1)) {
      days.add(new Date(time));
    }
    return days;
  }
}
