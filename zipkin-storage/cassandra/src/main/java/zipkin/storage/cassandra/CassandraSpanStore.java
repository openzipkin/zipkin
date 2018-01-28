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
package zipkin.storage.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.Dependencies;
import zipkin.internal.DependencyLinker;
import zipkin.internal.GroupByTraceId;
import zipkin.internal.MergeById;
import zipkin.internal.Nullable;
import zipkin.storage.QueryRequest;
import zipkin.storage.guava.GuavaSpanStore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.DiscreteDomain.integers;
import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static zipkin.internal.Util.getDays;

public final class CassandraSpanStore implements GuavaSpanStore {
  private static final Logger LOG = LoggerFactory.getLogger(CassandraSpanStore.class);

  static final ListenableFuture<List<String>> EMPTY_LIST =
      immediateFuture(Collections.<String>emptyList());

  private final int maxTraceCols;
  private final int indexFetchMultiplier;
  private final boolean strictTraceId;
  private final Session session;
  private final TimestampCodec timestampCodec;
  private final Set<Integer> buckets;
  private final PreparedStatement selectTraces;
  private final PreparedStatement selectDependencies;
  private final PreparedStatement selectServiceNames;
  private final PreparedStatement selectSpanNames;
  private final PreparedStatement selectTraceIdsByServiceName;
  private final PreparedStatement selectTraceIdsByServiceNames;
  private final PreparedStatement selectTraceIdsBySpanName;
  private final PreparedStatement selectTraceIdsByAnnotation;
  private final Function<ResultSet, Map<Long, Long>> traceIdToTimestamp;

  CassandraSpanStore(Session session, int bucketCount, int maxTraceCols, int indexFetchMultiplier,
      boolean strictTraceId) {
    this.session = session;
    this.maxTraceCols = maxTraceCols;
    this.indexFetchMultiplier = indexFetchMultiplier;
    this.strictTraceId = strictTraceId;

    ProtocolVersion protocolVersion = session.getCluster()
        .getConfiguration().getProtocolOptions().getProtocolVersion();
    this.timestampCodec = new TimestampCodec(protocolVersion);
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
            .from(Tables.SERVICE_NAMES));

    selectSpanNames = session.prepare(
        QueryBuilder.select("span_name")
            .from(Tables.SPAN_NAMES)
            .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
            .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
            .limit(QueryBuilder.bindMarker("limit_")));

    selectTraceIdsByServiceName = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(Tables.SERVICE_NAME_INDEX)
            .where(QueryBuilder.eq("service_name", QueryBuilder.bindMarker("service_name")))
            .and(QueryBuilder.in("bucket", QueryBuilder.bindMarker("bucket")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .orderBy(QueryBuilder.desc("ts")));

    selectTraceIdsBySpanName = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(Tables.SERVICE_SPAN_NAME_INDEX)
            .where(
                QueryBuilder.eq("service_span_name", QueryBuilder.bindMarker("service_span_name")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .orderBy(QueryBuilder.desc("ts")));

    selectTraceIdsByAnnotation = session.prepare(
        QueryBuilder.select("ts", "trace_id")
            .from(Tables.ANNOTATIONS_INDEX)
            .where(QueryBuilder.eq("annotation", QueryBuilder.bindMarker("annotation")))
            .and(QueryBuilder.in("bucket", QueryBuilder.bindMarker("bucket")))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .orderBy(QueryBuilder.desc("ts")));

    if (protocolVersion.compareTo(ProtocolVersion.V4) < 0) {
      LOG.warn("Please update Cassandra to 2.2 or later, as some features may fail");
      // Log vs failing on "Partition KEY part service_name cannot be restricted by IN relation"
      selectTraceIdsByServiceNames = null;
    } else {
      selectTraceIdsByServiceNames = session.prepare(
          QueryBuilder.select("ts", "trace_id")
              .from(Tables.SERVICE_NAME_INDEX)
              .where(QueryBuilder.in("service_name", QueryBuilder.bindMarker("service_name")))
              .and(QueryBuilder.in("bucket", QueryBuilder.bindMarker("bucket")))
              .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
              .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
              .limit(QueryBuilder.bindMarker("limit_"))
              .orderBy(QueryBuilder.desc("ts")));
    }

    traceIdToTimestamp = new Function<ResultSet, Map<Long, Long>>() {
      @Override public Map<Long, Long> apply(ResultSet input) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Row row : input) {
          result.put(row.getLong("trace_id"), timestampCodec.deserialize(row, "ts"));
        }
        return result;
      }
    };
  }

  /**
   * This fans out into a potentially large amount of requests related to the amount of annotations
   * queried. The returned future will fail if any of the inputs fail.
   *
   * <p>When {@link QueryRequest#serviceName service name} is unset, service names will be
   * fetched eagerly, implying an additional query.
   */
  @Override
  public ListenableFuture<List<List<Span>>> getTraces(final QueryRequest request) {
    checkArgument(request.minDuration == null,
      "getTraces with duration is unsupported. Upgrade to the new cassandra3 schema.");
    // Over fetch on indexes as they don't return distinct (trace id, timestamp) rows.
    final int traceIndexFetchSize = request.limit * indexFetchMultiplier;
    ListenableFuture<Map<Long, Long>> traceIdToTimestamp;
    if (request.spanName != null) {
      traceIdToTimestamp = getTraceIdsBySpanName(request.serviceName, request.spanName,
          request.endTs * 1000, request.lookback * 1000, traceIndexFetchSize);
    } else if (request.serviceName != null) {
      traceIdToTimestamp = getTraceIdsByServiceNames(Collections.singletonList(request.serviceName),
          request.endTs * 1000, request.lookback * 1000, traceIndexFetchSize);
    } else {
      checkArgument(selectTraceIdsByServiceNames != null,
          "getTraces without serviceName requires Cassandra 2.2 or later");
      traceIdToTimestamp = transform(getServiceNames(),
          new AsyncFunction<List<String>, Map<Long, Long>>() {
            @Override
            public ListenableFuture<Map<Long, Long>> apply(@Nullable List<String> serviceNames) {
              return getTraceIdsByServiceNames(serviceNames,
                  request.endTs * 1000, request.lookback * 1000, traceIndexFetchSize);
            }
          });
    }

    List<String> annotationKeys = CassandraUtil.annotationKeys(request);

    ListenableFuture<Set<Long>> traceIds;
    if (annotationKeys.isEmpty()) {
      // Simplest case is when there is no annotation query. Limit is valid since there's no AND
      // query that could reduce the results returned to less than the limit.
      traceIds = Futures.transform(traceIdToTimestamp, CassandraUtil.keyset());
    } else {
      // While a valid port of the scala cassandra span store (from zipkin 1.35), there is a fault.
      // each annotation key is an intersection, meaning we likely return < traceIndexFetchSize.
      List<ListenableFuture<Map<Long, Long>>> futureKeySetsToIntersect = new ArrayList<>();
      if (request.spanName != null) {
        futureKeySetsToIntersect.add(traceIdToTimestamp);
      }
      for (String annotationKey : annotationKeys) {
        futureKeySetsToIntersect.add(getTraceIdsByAnnotation(annotationKey,
            request.endTs * 1000, request.lookback * 1000, traceIndexFetchSize));
      }
      // We achieve the AND goal, by intersecting each of the key sets.
      traceIds = Futures.transform(allAsList(futureKeySetsToIntersect), CassandraUtil.intersectKeySets());
    }
    return transform(traceIds, new AsyncFunction<Set<Long>, List<List<Span>>>() {
      @Override
      public ListenableFuture<List<List<Span>>> apply(@Nullable Set<Long> traceIds) {
        traceIds = ImmutableSet.copyOf(Iterators.limit(traceIds.iterator(), request.limit));
        return transform(getSpansByTraceIds(traceIds, maxTraceCols),
            new Function<List<Span>, List<List<Span>>>() {
              @Override public List<List<Span>> apply(@Nullable List<Span> input) {
                // Indexes only contain Span.traceId, so our matches are imprecise on Span.traceIdHigh
                return FluentIterable.from(GroupByTraceId.apply(input, strictTraceId, true))
                    .filter(new Predicate<List<Span>>() {
                      @Override public boolean apply(List<Span> trace) {
                        return trace.get(0).traceIdHigh == 0 || request.test(trace);
                      }
                    })
                    .toList();
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

  /**
   * Since the schema doesn't have a unique index on {@link Span#traceIdHigh}, we have to filter
   * client-side.
   */
  @Override public ListenableFuture<List<Span>> getRawTrace(final long traceIdHigh, long traceIdLow) {
    return transform(getSpansByTraceIds(Collections.singleton(traceIdLow), maxTraceCols),
        new Function<List<Span>, List<Span>>() {
          @Override public List<Span> apply(@Nullable List<Span> input) {
            if (strictTraceId) {
              Iterator<Span> spans = input.iterator();
              while (spans.hasNext()) {
                long nextTraceIdHigh = spans.next().traceIdHigh;
                if (nextTraceIdHigh != 0L && nextTraceIdHigh != traceIdHigh) {
                  spans.remove();
                }
              }
            }
            return input.isEmpty() ? null : input;
          }
        });
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceId) {
    return getTrace(0L, traceId);
  }

  @Override public ListenableFuture<List<Span>> getTrace(long traceIdHigh, long traceIdLow) {
    return transform(getRawTrace(traceIdHigh, traceIdLow), AdjustTrace.INSTANCE);
  }

  enum AdjustTrace implements Function<Collection<Span>, List<Span>> {
    INSTANCE;

    @Override public List<Span> apply(@Nullable Collection<Span> input) {
      List<Span> result = CorrectForClockSkew.apply(MergeById.apply(input));
      return result.isEmpty() ? null : result;
    }

    @Override public String toString(){
      return "AdjustTrace";
    }
  }

  @Override public ListenableFuture<List<String>> getServiceNames() {
    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectServiceNames, "select-service-names");
      return transform(session.executeAsync(bound), new Function<ResultSet, List<String>>() {
            @Override public List<String> apply(@Nullable ResultSet input) {
              Set<String> serviceNames = new HashSet<>();
              for (Row row : input) {
                serviceNames.add(row.getString("service_name"));
              }
              return Ordering.natural().sortedCopy(serviceNames);
            }
          }
      );
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  @Override public ListenableFuture<List<String>> getSpanNames(String serviceName) {
    if (serviceName == null || serviceName.isEmpty()) return EMPTY_LIST;
    serviceName = checkNotNull(serviceName, "serviceName").toLowerCase();
    int bucket = 0;
    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectSpanNames, "select-span-names")
          .setString("service_name", serviceName)
          .setInt("bucket", bucket)
          // no one is ever going to browse so many span names
          .setInt("limit_", 1000);

      return transform(session.executeAsync(bound), new Function<ResultSet, List<String>>() {
            @Override public List<String> apply(@Nullable ResultSet input) {
              Set<String> spanNames = new HashSet<>();
              for (Row row : input) {
                spanNames.add(row.getString("span_name"));
              }
              return Ordering.natural().sortedCopy(spanNames);
            }
          }
      );
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

    @Override public List<DependencyLink> apply(@Nullable ResultSet rs) {
      ImmutableList.Builder<DependencyLink> unmerged = ImmutableList.builder();
      for (Row row : rs) {
        ByteBuffer encodedDayOfDependencies = row.getBytes("dependencies");
        for (DependencyLink link : Dependencies.fromThrift(encodedDayOfDependencies).links) {
          unmerged.add(link);
        }
      }
      return DependencyLinker.merge(unmerged.build());
    }

    @Override public String toString(){
      return "MergeDependencies";
    }
  }

  /**
   * Get the available trace information from the storage system. Spans in trace should be sorted by
   * the first annotation timestamp in that span. First event should be first in the spans list. <p>
   * The return list will contain only spans that have been found, thus the return list may not
   * match the provided list of ids.
   */
  ListenableFuture<List<Span>> getSpansByTraceIds(Set<Long> traceIds, int limit) {
    checkNotNull(traceIds, "traceIds");
    if (traceIds.isEmpty()) {
      return immediateFuture(Collections.<Span>emptyList());
    }

    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectTraces, "select-traces")
          .setSet("trace_id", traceIds)
          .setInt("limit_", limit);

      bound.setFetchSize(Integer.MAX_VALUE);

      return transform(session.executeAsync(bound),
          new Function<ResultSet, List<Span>>() {
            @Override public List<Span> apply(@Nullable ResultSet input) {
              List<Span> result = new ArrayList<>(input.getAvailableWithoutFetching());
              for (Row row : input) {
                result.add(Codec.THRIFT.readSpan(row.getBytes("span")));
              }
              return result;
            }
          }
      );
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  ListenableFuture<Map<Long, Long>> getTraceIdsByServiceNames(List<String> serviceNames, long endTs,
      long lookback, int limit) {
    if (serviceNames.isEmpty()) return immediateFuture(Collections.<Long, Long>emptyMap());

    long startTs = Math.max(endTs - lookback, 0); // >= 1970
    try {
      // This guards use of "in" query to give people a little more time to move off Cassandra 2.1
      // Note that it will still fail when serviceNames.size() > 1
      BoundStatement bound = serviceNames.size() == 1 ?
          CassandraUtil.bindWithName(selectTraceIdsByServiceName, "select-trace-ids-by-service-name")
              .setString("service_name", serviceNames.get(0))
              .setSet("bucket", buckets)
              .setBytesUnsafe("start_ts", timestampCodec.serialize(startTs))
              .setBytesUnsafe("end_ts", timestampCodec.serialize(endTs))
              .setInt("limit_", limit) :
          CassandraUtil.bindWithName(selectTraceIdsByServiceNames, "select-trace-ids-by-service-names")
              .setList("service_name", serviceNames)
              .setSet("bucket", buckets)
              .setBytesUnsafe("start_ts", timestampCodec.serialize(startTs))
              .setBytesUnsafe("end_ts", timestampCodec.serialize(endTs))
              .setInt("limit_", limit);

      bound.setFetchSize(Integer.MAX_VALUE);

      return transform(session.executeAsync(bound), traceIdToTimestamp);
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  ListenableFuture<Map<Long, Long>> getTraceIdsBySpanName(String serviceName,
      String spanName, long endTs, long lookback, int limit) {
    checkArgument(serviceName != null, "serviceName required on spanName query");
    checkArgument(spanName != null, "spanName required on spanName query");
    String serviceSpanName = serviceName + "." + spanName;
    long startTs = Math.max(endTs - lookback, 0); // >= 1970
    try {
      BoundStatement bound = CassandraUtil.bindWithName(selectTraceIdsBySpanName, "select-trace-ids-by-span-name")
          .setString("service_span_name", serviceSpanName)
          .setBytesUnsafe("start_ts", timestampCodec.serialize(startTs))
          .setBytesUnsafe("end_ts", timestampCodec.serialize(endTs))
          .setInt("limit_", limit);

      return transform(session.executeAsync(bound), traceIdToTimestamp);
    } catch (RuntimeException ex) {
      return immediateFailedFuture(ex);
    }
  }

  ListenableFuture<Map<Long, Long>> getTraceIdsByAnnotation(String annotationKey,
      long endTs, long lookback, int limit) {
    long startTs = Math.max(endTs - lookback, 0); // >= 1970
    try {
      BoundStatement bound =
          CassandraUtil.bindWithName(selectTraceIdsByAnnotation, "select-trace-ids-by-annotation")
              .setBytes("annotation", CassandraUtil.toByteBuffer(annotationKey))
              .setSet("bucket", buckets)
              .setBytesUnsafe("start_ts", timestampCodec.serialize(startTs))
              .setBytesUnsafe("end_ts", timestampCodec.serialize(endTs))
              .setInt("limit_", limit);

      bound.setFetchSize(Integer.MAX_VALUE);

      return transform(session.executeAsync(bound), new Function<ResultSet, Map<Long, Long>>() {
            @Override public Map<Long, Long> apply(@Nullable ResultSet input) {
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
      return immediateFailedFuture(ex);
    }
  }
}
