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
package zipkin.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.CorrectForClockSkew;
import zipkin.internal.DependencyLinker;
import zipkin.internal.GroupByTraceId;
import zipkin.internal.MergeById;
import zipkin.internal.Nullable;
import zipkin.internal.Pair;

import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.GroupByTraceId.TRACE_DESCENDING;
import static zipkin.internal.Util.sortedList;

/** Internally, spans are indexed on 64-bit trace ID */
public final class InMemorySpanStore implements SpanStore {
  /**
   * Primary source of data is this map, which includes spans ordered descending by timestamp. All
   * other maps are derived from the span values here. This uses a list for the spans, so that it is
   * visible (via /api/v1/trace/id?raw) when instrumentation report the same spans multiple times.
   *
   * <p>In the future, we will bound data. This implies some mechanism of cascading deletes based on
   * the pair used as the key here. One implementation could be to make one dataset have a strong
   * reference to the pair of (traceId, timestamp), and others weak. For example, making this a
   * weak reference and traceIdToTraceIdTimeStamps a strong one. In that case, deleting the trace ID
   * from traceIdToTraceIdTimeStamps would lead to a purge of spans at GC time.
   */
  private final SortedMultimap<Pair<Long>, Span> spansByTraceIdTimeStamp =
      new LinkedListSortedMultimap<>(VALUE_2_DESCENDING);

  /** This supports span lookup by {@link zipkin.Span#traceId lower 64-bits of the trace ID} */
  private final SortedMultimap<Long, Pair<Long>> traceIdToTraceIdTimeStamps =
      new LinkedHashSetSortedMultimap<>(Long::compareTo);
  /**
   * This supports span lookup by {@link zipkin.Endpoint#serviceName service name}.
   *
   * <p>QueryRequest.limit needs trace ids are returned in timestamp descending order.
   */
  private final SortedMultimap<String, Pair<Long>> serviceToTraceIdTimestamp =
      new TreeSetSortedMultimap<>(String::compareTo, VALUE_2_DESCENDING);
  /** This is an index of {@link Span#name} by {@link zipkin.Endpoint#serviceName service name} */
  private final SortedMultimap<String, String> serviceToSpanNames =
      new LinkedHashSetSortedMultimap<>(String::compareTo);

  private final boolean strictTraceId;
  volatile int acceptedSpanCount;

  // Historical constructor
  public InMemorySpanStore() {
    this(new InMemoryStorage.Builder());
  }

  InMemorySpanStore(InMemoryStorage.Builder builder) {
    this.strictTraceId = builder.strictTraceId;
  }

  final StorageAdapters.SpanConsumer spanConsumer = new StorageAdapters.SpanConsumer() {
    @Override public void accept(List<Span> spans) {
      for (Span span : spans) {
        Long timestamp = guessTimestamp(span);
        Pair<Long> traceIdTimeStamp =
            Pair.create(span.traceId, timestamp == null ? Long.MIN_VALUE : timestamp);
        String spanName = span.name;
        synchronized (InMemorySpanStore.this) {
          spansByTraceIdTimeStamp.put(traceIdTimeStamp, span);
          traceIdToTraceIdTimeStamps.put(span.traceId, traceIdTimeStamp);
          acceptedSpanCount++;

          for (String serviceName : span.serviceNames()) {
            serviceToTraceIdTimestamp.put(serviceName, traceIdTimeStamp);
            serviceToSpanNames.put(serviceName, spanName);
          }
        }
      }
    }

    @Override public String toString() {
      return "InMemorySpanConsumer";
    }
  };

  /**
   * @deprecated use {@link #getRawTraces()}
   */
  @Deprecated
  public synchronized List<Long> traceIds() {
    return sortedList(traceIdToTraceIdTimeStamps.keySet());
  }

  synchronized void clear() {
    acceptedSpanCount = 0;
    traceIdToTraceIdTimeStamps.clear();
    spansByTraceIdTimeStamp.clear();
    serviceToTraceIdTimestamp.clear();
    serviceToSpanNames.clear();
  }

  /**
   * Used for testing. Returns all traces unconditionally.
   */
  public synchronized List<List<Span>> getRawTraces() {
    List<List<Span>> result = new ArrayList<>();
    for (long traceId : traceIdToTraceIdTimeStamps.keySet()) {
      Collection<Span> sameTraceId = spansByTraceId(traceId);
      for (List<Span> next : GroupByTraceId.apply(sameTraceId, strictTraceId, false)) {
        result.add(next);
      }
    }
    Collections.sort(result, TRACE_DESCENDING);
    return result;
  }

  @Override
  public synchronized List<List<Span>> getTraces(QueryRequest request) {
    Set<Long> traceIdsInTimerange = traceIdsDescendingByTimestamp(request);
    if (traceIdsInTimerange.isEmpty()) return Collections.emptyList();

    List<List<Span>> result = new ArrayList<>();
    for (Iterator<Long> traceId = traceIdsInTimerange.iterator();
        traceId.hasNext() && result.size() < request.limit; ) {
      Collection<Span> sameTraceId = spansByTraceId(traceId.next());
      for (List<Span> next : GroupByTraceId.apply(sameTraceId, strictTraceId, true)) {
        if (request.test(next)) {
          result.add(next);
        }
      }
    }
    Collections.sort(result, TRACE_DESCENDING);
    return result;
  }

  Set<Long> traceIdsDescendingByTimestamp(QueryRequest request) {
    Collection<Pair<Long>> traceIdTimestamps = request.serviceName != null
        ? serviceToTraceIdTimestamp.get(request.serviceName)
        : spansByTraceIdTimeStamp.keySet();

    long endTs = request.endTs * 1000;
    long startTs = endTs - request.lookback * 1000;

    if (traceIdTimestamps == null || traceIdTimestamps.isEmpty()) return Collections.emptySet();
    Set<Long> result = new LinkedHashSet<>();
    for (Pair<Long> traceIdTimestamp : traceIdTimestamps) {
      if (traceIdTimestamp._2 >= startTs || traceIdTimestamp._2 <= endTs) {
        result.add(traceIdTimestamp._1);
      }
    }
    return result;
  }

  @Override public List<Span> getTrace(long traceId) {
    return getTrace(0L, traceId);
  }

  @Override public List<Span> getTrace(long traceIdHigh, long traceIdLow) {
    List<Span> result = getRawTrace(traceIdHigh, traceIdLow);
    if (result == null) return null;
    return CorrectForClockSkew.apply(MergeById.apply(result));
  }

  @Override public List<Span> getRawTrace(long traceId) {
    return getRawTrace(0L, traceId);
  }

  @Override public synchronized List<Span> getRawTrace(long traceIdHigh, long traceId) {
    List<Span> spans = (List<Span>) spansByTraceId(traceId);
    if (spans == null || spans.isEmpty()) return null;
    if (!strictTraceId) return sortedList(spans);

    List<Span> filtered = new ArrayList<>(spans);
    Iterator<Span> iterator = filtered.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().traceIdHigh != traceIdHigh) {
        iterator.remove();
      }
    }
    return filtered.isEmpty() ? null : filtered;
  }

  @Override
  public synchronized List<String> getServiceNames() {
    return sortedList(serviceToTraceIdTimestamp.keySet());
  }

  @Override
  public synchronized List<String> getSpanNames(String service) {
    if (service == null) return Collections.emptyList();
    service = service.toLowerCase(); // service names are always lowercase!
    return sortedList(serviceToSpanNames.get(service));
  }

  @Override
  public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    QueryRequest request = QueryRequest.builder()
        .endTs(endTs)
        .lookback(lookback)
        .limit(Integer.MAX_VALUE).build();

    DependencyLinker linksBuilder = new DependencyLinker();
    for (Collection<Span> trace : getTraces(request)) {
      linksBuilder.putTrace(trace);
    }
    return linksBuilder.link();
  }

  static final class LinkedListSortedMultimap<K, V> extends SortedMultimap<K, V> {
    LinkedListSortedMultimap(Comparator<K> comparator) {
      super(comparator);
    }

    @Override Collection<V> valueContainer() {
      return new LinkedList<>();
    }
  }

  static final Comparator<Pair<Long>> VALUE_2_DESCENDING = (left, right) -> {
    int result = right._2.compareTo(left._2);
    if (result != 0) return result;
    return right._1.compareTo(left._1);
  };

  static final class TreeSetSortedMultimap<K, V> extends SortedMultimap<K, V> {
    final Comparator<V> valueComparator;

    TreeSetSortedMultimap(Comparator<K> keyComparator, Comparator<V> valueComparator) {
      super(keyComparator);
      this.valueComparator = valueComparator;
    }

    @Override Set<V> valueContainer() {
      return new TreeSet<>(valueComparator);
    }
  }

  static final class LinkedHashSetSortedMultimap<K, V> extends SortedMultimap<K, V> {
    LinkedHashSetSortedMultimap(Comparator<K> comparator) {
      super(comparator);
    }

    @Override Collection<V> valueContainer() {
      return new LinkedHashSet<>();
    }
  }

  static abstract class SortedMultimap<K, V> {
    private final TreeMap<K, Collection<V>> delegate;

    SortedMultimap(Comparator<K> comparator) {
      delegate = new TreeMap<>(comparator);
    }

    abstract Collection<V> valueContainer();

    Set<K> keySet() {
      return delegate.keySet();
    }

    void put(K key, V value) {
      Collection<V> valueContainer = delegate.get(key);
      if (valueContainer == null) {
        synchronized (delegate) {
          if (!delegate.containsKey(key)) {
            valueContainer = valueContainer();
            delegate.put(key, valueContainer);
          }
        }
      }
      valueContainer.add(value);
    }

    // not synchronized as only used for for testing
    void clear() {
      delegate.clear();
    }

    Collection<V> get(K key) {
      Collection<V> result = delegate.get(key);
      return result != null ? result : Collections.emptySet();
    }
  }

  private Collection<Span> spansByTraceId(long traceId) {
    Collection<Span> sameTraceId = new ArrayList<>();
    for (Pair<Long> traceIdTimestamp : traceIdToTraceIdTimeStamps.get(traceId)) {
      sameTraceId.addAll(spansByTraceIdTimeStamp.get(traceIdTimestamp));
    }
    return sameTraceId;
  }
}
