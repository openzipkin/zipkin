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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.twitter.zipkin.storage.cassandra.Repository;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.Dependencies;
import zipkin.internal.MergeById;
import zipkin.internal.Nullable;
import zipkin.internal.Pair;
import zipkin.internal.ThriftCodec;

import static com.google.common.util.concurrent.Futures.allAsList;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static java.lang.String.format;
import static zipkin.cassandra.CassandraUtil.annotationKeys;
import static zipkin.internal.Util.midnightUTC;
import static zipkin.internal.Util.sortedList;

/**
 * CQL3 implementation of a span store.
 *
 * <p>This uses zipkin-cassandra-core which packages "/cassandra-schema-cql3.txt"
 */
public final class CassandraSpanStore implements SpanStore, AutoCloseable {
  static final ThriftCodec THRIFT_CODEC = new ThriftCodec();
  static final Comparator<List<Span>> TRACE_DESCENDING = new Comparator<List<Span>>() {
    @Override
    public int compare(List<Span> left, List<Span> right) {
      return right.get(0).compareTo(left.get(0));
    }
  };

  private final String keyspace;
  private final int indexTtl;
  private final int maxTraceCols;
  private final Cluster cluster;
  final Repository repository;
  private final CassandraSpanConsumer spanConsumer;

  public CassandraSpanStore(CassandraConfig config) {
    this.keyspace = config.keyspace;
    this.indexTtl = config.indexTtl;
    this.maxTraceCols = config.maxTraceCols;
    this.cluster = config.toCluster();
    this.repository = new Repository(config.keyspace, cluster, config.ensureSchema);
    this.spanConsumer = new CassandraSpanConsumer(repository, config.spanTtl, config.indexTtl);
  }

  @Override
  public void accept(List<Span> spans) {
    spanConsumer.accept(spans);
  }

  @Override
  public List<List<Span>> getTraces(QueryRequest request) {
    String spanName = request.spanName != null ? request.spanName : "";
    final ListenableFuture<Map<Long, Long>> traceIdToTimestamp;
    if (request.minDuration != null || request.maxDuration != null) {
      traceIdToTimestamp = repository.getTraceIdsByDuration(request.serviceName, spanName,
          request.minDuration, request.maxDuration != null ? request.maxDuration : Long.MAX_VALUE,
          request.endTs * 1000, (request.endTs - request.lookback) * 1000, request.limit, indexTtl);
    } else if (!spanName.isEmpty()) {
      traceIdToTimestamp = repository.getTraceIdsBySpanName(request.serviceName, spanName,
          request.endTs * 1000, request.lookback * 1000, request.limit);
    } else {
      traceIdToTimestamp = repository.getTraceIdsByServiceName(request.serviceName,
          request.endTs * 1000, request.lookback * 1000, request.limit);
    }

    List<ByteBuffer> annotationKeys = annotationKeys(request);

    // Simplest case is when there is no annotation query. Limit is valid since there's no AND query
    // that could reduce the results returned to less than the limit.
    if (annotationKeys.isEmpty()) {
      return getTracesByIds(getUnchecked(traceIdToTimestamp).keySet());
    }

    // While a valid port of the scala cassandra span store (from zipkin 1.35), there is a fault.
    // each annotation key is an intersection, which means we are likely to return less than limit.
    List<ListenableFuture<Map<Long, Long>>> futureKeySetsToIntersect = new ArrayList<>();
    futureKeySetsToIntersect.add(traceIdToTimestamp);
    for (ByteBuffer annotationKey : annotationKeys) {
      futureKeySetsToIntersect.add(repository.getTraceIdsByAnnotation(annotationKey,
          request.endTs * 1000, request.lookback * 1000, request.limit));
    }

    // We achieve the AND goal, by intersecting each of the key sets.
    List<Map<Long, Long>> keySetsToIntersect = getUnchecked(allAsList(futureKeySetsToIntersect));
    Set<Long> traceIds = Sets.newLinkedHashSet(keySetsToIntersect.get(0).keySet());
    for (int i = 1; i < keySetsToIntersect.size(); i++) {
      traceIds.retainAll(keySetsToIntersect.get(i).keySet());
    }

    return getTracesByIds(traceIds);
  }

  @Override
  public List<List<Span>> getTracesByIds(Collection<Long> traceIds) {
    // Synchronously get the encoded data, as there are no other requests to the backend needed.
    Collection<List<ByteBuffer>> encodedTraces = getUnchecked(
        repository.getSpansByTraceIds(traceIds.toArray(new Long[traceIds.size()]), maxTraceCols)
    ).values();

    List<List<Span>> result = new LinkedList<>(); // merging will imply a different size
    for (List<ByteBuffer> encodedTrace : encodedTraces) {
      List<Span> spans = new ArrayList<>(encodedTrace.size());
      for (ByteBuffer encodedSpan : encodedTrace) {
        spans.add(THRIFT_CODEC.readSpan(encodedSpan));
      }
      result.add(CorrectForClockSkew.apply(MergeById.apply(spans)));
    }
    Collections.sort(result, TRACE_DESCENDING);
    return result;
  }

  @Override
  public List<String> getServiceNames() {
    return sortedList(getUnchecked(repository.getServiceNames()));
  }

  @Override
  public List<String> getSpanNames(String service) {
    if (service == null) return Collections.emptyList();
    service = service.toLowerCase(); // service names are always lowercase!
    return sortedList(getUnchecked(repository.getSpanNames(service)));
  }

  @Override
  public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    long endEpochDayMillis = midnightUTC(endTs);
    long startEpochDayMillis = midnightUTC(endTs - (lookback != null ? lookback : endTs));

    // Synchronously get the encoded data, as there are no other requests to the backend needed.
    List<ByteBuffer> encodedDailyDependencies =
        getUnchecked(repository.getDependencies(startEpochDayMillis, endEpochDayMillis));

    // Combine the dependency links from startEpochDayMillis until endEpochDayMillis
    Map<Pair<String>, Long> links = new LinkedHashMap<>(encodedDailyDependencies.size());

    for (ByteBuffer encodedDayOfDependencies : encodedDailyDependencies) {
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

  /** Used for testing */
  void clear() {
    try (Session session = cluster.connect()) {
      List<ListenableFuture<?>> futures = new LinkedList<>();
      for (String cf : Arrays.asList(
          "traces",
          "dependencies",
          "service_names",
          "span_names",
          "service_name_index",
          "service_span_name_index",
          "annotations_index",
          "span_duration_index"
      )) {
        futures.add(session.executeAsync(format("TRUNCATE %s.%s", keyspace, cf)));
      }
      Futures.getUnchecked(Futures.allAsList(futures));
    }
  }

  @Override
  public void close() {
    repository.close();
    cluster.close();
  }
}
