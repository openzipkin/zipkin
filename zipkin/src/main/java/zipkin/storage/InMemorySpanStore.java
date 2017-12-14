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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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

/**
 * Internally, spans are indexed on 64-bit trace ID
 *
 * <p>Here's an example of some traces in memory:
 *
 * <pre>{@code
 * spansByTraceIdTimeStamp:
 *    <aaaa,July 4> --> ( spanA(time:July 4, traceId:aaaa, service:foo, name:GET),
 *                        spanB(time:July 4, traceId:aaaa, service:bar, name:GET) )
 *    <cccc,July 4> --> ( spanC(time:July 4, traceId:aaaa, service:foo, name:GET) )
 *    <bbbb,July 5> --> ( spanD(time:July 5, traceId:bbbb, service:biz, name:GET) )
 *    <bbbb,July 6> --> ( spanE(time:July 6, traceId:bbbb) service:foo, name:POST )
 *
 * traceIdToTraceIdTimeStamps:
 *    aaaa --> [ <aaaa,July 4> ]
 *    bbbb --> [ <bbbb,July 5>, <bbbb,July 6> ]
 *    cccc --> [ <cccc,July 4> ]
 *
 * serviceToTraceIds:
 *    foo --> [ <aaaa>, <cccc>, <bbbb> ]
 *    bar --> [ <aaaa> ]
 *    biz --> [ <bbbb> ]
 *
 * serviceToSpanNames:
 *    bar --> ( GET )
 *    biz --> ( GET )
 *    foo --> ( GET, POST )
 * }</pre>
 */
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
      new LinkedHashSetSortedMultimap<>(LONG_COMPARATOR);
  /** This is an index of {@link Span#traceId} by {@link zipkin.Endpoint#serviceName service name} */
  private final ServiceNameToTraceIds serviceToTraceIds = new ServiceNameToTraceIds();
  /** This is an index of {@link Span#name} by {@link zipkin.Endpoint#serviceName service name} */
  private final SortedMultimap<String, String> serviceToSpanNames =
      new LinkedHashSetSortedMultimap<>(STRING_COMPARATOR);

  private final boolean strictTraceId;
  final int maxSpanCount;
  volatile int acceptedSpanCount;

  // Historical constructor
  public InMemorySpanStore() {
    this(new InMemoryStorage.Builder());
  }

  InMemorySpanStore(InMemoryStorage.Builder builder) {
    this.strictTraceId = builder.strictTraceId;
    this.maxSpanCount = builder.maxSpanCount;
  }

  final StorageAdapters.SpanConsumer spanConsumer = new StorageAdapters.SpanConsumer() {
    @Override public void accept(List<Span> spans) {
      if (spans.isEmpty()) return;
      if (spans.size() > maxSpanCount) {
        spans = spans.subList(0, maxSpanCount);
      }
      addSpans(spans);
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
    serviceToTraceIds.clear();
    serviceToSpanNames.clear();
  }

  synchronized void addSpans(List<Span> spans) {
    int delta = spans.size();
    int spansToRecover = (spansByTraceIdTimeStamp.size() + delta) - maxSpanCount;
    evictToRecoverSpans(spansToRecover);
    for (Span span : spans) {
      Long timestamp = guessTimestamp(span);
      Pair<Long> traceIdTimeStamp =
        Pair.create(span.traceId, timestamp == null ? Long.MIN_VALUE : timestamp);
      String spanName = span.name;
      spansByTraceIdTimeStamp.put(traceIdTimeStamp, span);
      traceIdToTraceIdTimeStamps.put(span.traceId, traceIdTimeStamp);
      acceptedSpanCount++;

      for (String serviceName : span.serviceNames()) {
        serviceToTraceIds.put(serviceName, span.traceId);
        serviceToSpanNames.put(serviceName, spanName);
      }
    }
  }

  /** Returns the count of spans evicted. */
  int evictToRecoverSpans(int spansToRecover) {
    int spansEvicted = 0;
    while (spansToRecover > 0) {
      int spansInOldestTrace = deleteOldestTrace();
      spansToRecover -= spansInOldestTrace;
      spansEvicted += spansInOldestTrace;
    }
    return spansEvicted;
  }

  /** Returns the count of spans evicted. */
  private int deleteOldestTrace() {
    int spansEvicted = 0;
    long traceId = spansByTraceIdTimeStamp.delegate.lastKey()._1;
    Collection<Pair<Long>> traceIdTimeStamps = traceIdToTraceIdTimeStamps.remove(traceId);
    for (Iterator<Pair<Long>> traceIdTimeStampIter = traceIdTimeStamps.iterator();
      traceIdTimeStampIter.hasNext(); ) {
      Pair<Long> traceIdTimeStamp = traceIdTimeStampIter.next();
      Collection<Span> spans = spansByTraceIdTimeStamp.remove(traceIdTimeStamp);
      spansEvicted += spans.size();
    }
    for (String orphanedService : serviceToTraceIds.removeServiceIfTraceId(traceId)) {
      serviceToSpanNames.remove(orphanedService);
    }
    return spansEvicted;
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

  @Override public List<List<Span>> getTraces(QueryRequest request) {
    return getTraces(request, strictTraceId);
  }

  synchronized List<List<Span>> getTraces(QueryRequest request, boolean strictTraceId) {
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
        ? traceIdTimestampsByServiceName(request.serviceName)
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

  @Override public synchronized List<Span> getTrace(long traceId) {
    return getTrace(0L, traceId);
  }

  @Override public synchronized List<Span> getTrace(long traceIdHigh, long traceIdLow) {
    List<Span> result = getRawTrace(traceIdHigh, traceIdLow);
    if (result == null) return null;
    return CorrectForClockSkew.apply(MergeById.apply(result));
  }

  @Override public synchronized List<Span> getRawTrace(long traceId) {
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
    return sortedList(serviceToTraceIds.keySet());
  }

  @Override
  public synchronized List<String> getSpanNames(String service) {
    if (service == null) return Collections.emptyList();
    service = service.toLowerCase(); // service names are always lowercase!
    return sortedList(serviceToSpanNames.get(service));
  }

  @Override
  public synchronized List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    QueryRequest request = QueryRequest.builder()
        .endTs(endTs)
        .lookback(lookback)
        .limit(Integer.MAX_VALUE).build();

    DependencyLinker linksBuilder = new DependencyLinker();

    // We don't have a query parameter for strictTraceId when fetching dependency links, so we
    // ignore traceIdHigh. Otherwise, a single trace can appear as two, doubling callCount.
    for (Collection<Span> trace : getTraces(request, /* strictTraceId */false)) {
      linksBuilder.putTrace(trace);
    }
    return linksBuilder.link();
  }

  static final class LinkedListSortedMultimap<K, V> extends SortedMultimap<K, V> {
    LinkedListSortedMultimap(Comparator<K> comparator) {
      super(comparator);
    }

    @Override Collection<V> valueContainer() {
      return new ArrayList<>();
    }
  }

  static final Comparator<String> STRING_COMPARATOR = new Comparator<String>() {
    @Override public int compare(String left, String right) {
      if (left == null) return -1;
      return left.compareTo(right);
    }

    @Override public String toString() {
      return "String::compareTo";
    }
  };

  static final Comparator<Long> LONG_COMPARATOR = new Comparator<Long>() {
    @Override public int compare(Long x, Long y) {
      if (x == null) return -1;
      return (x < y) ? -1 : ((x.equals(y)) ? 0 : 1);
    }

    @Override public String toString() {
      return "Long::compareTo"; // Long.compareTo is JRE 7+
    }
  };

  static final Comparator<Pair<Long>> VALUE_2_DESCENDING = new Comparator<Pair<Long>>() {
    @Override public int compare(Pair<Long> left, Pair<Long> right) {
      long x = left._2, y = right._2;
      int result = (x < y) ? -1 : ((x == y) ? 0 : 1); // Long.compareTo is JRE 7+
      if (result != 0) return -result; // use negative as we are descending
      // secondary compare as TreeMap is in use
      x = left._1;
      y = right._1;
      return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    @Override public String toString() {
      return "Value2Descending{}";
    }
  };

  static final class ServiceNameToTraceIds extends SortedMultimap<String, Long> {
    ServiceNameToTraceIds() {
      super(STRING_COMPARATOR);
    }

    @Override Set<Long> valueContainer() {
      return new LinkedHashSet<>();
    }

    /** Returns service names orphaned by removing the trace ID */
    Set<String> removeServiceIfTraceId(long traceId) {
      Set<String> result = new LinkedHashSet<>();
      for (Map.Entry<String, Collection<Long>> entry : delegate.entrySet()) {
        Collection<Long> traceIds = entry.getValue();
        if (traceIds.remove(traceId) && traceIds.isEmpty()) {
          result.add(entry.getKey());
        }
      }
      delegate.keySet().removeAll(result);
      return result;
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

  // Not synchronized as every exposed method on the enclosing type is
  static abstract class SortedMultimap<K, V> {
    final TreeMap<K, Collection<V>> delegate;
    int size = 0;

    SortedMultimap(Comparator<K> comparator) {
      delegate = new TreeMap<>(comparator);
    }

    abstract Collection<V> valueContainer();

    Set<K> keySet() {
      return delegate.keySet();
    }

    int size() {
      return size;
    }

    void put(K key, V value) {
      Collection<V> valueContainer = delegate.get(key);
      if (valueContainer == null) {
        delegate.put(key, valueContainer = valueContainer());
      }
      if (valueContainer.add(value)) size++;
    }

    Collection<V> remove(K key) {
      Collection<V> value = delegate.remove(key);
      if (value != null) size -= value.size();
      return value;
    }

    void clear() {
      delegate.clear();
      size = 0;
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

  private Collection<Pair<Long>> traceIdTimestampsByServiceName(String serviceName) {
    List<Pair<Long>> traceIdTimestamps = new ArrayList<>();
    for (long traceId : serviceToTraceIds.get(serviceName)) {
      traceIdTimestamps.addAll(traceIdToTraceIdTimeStamps.get(traceId));
    }
    Collections.sort(traceIdTimestamps, VALUE_2_DESCENDING);
    return traceIdTimestamps;
  }
}
