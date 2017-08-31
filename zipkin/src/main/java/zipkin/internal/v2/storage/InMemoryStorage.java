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
package zipkin.internal.v2.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import zipkin.DependencyLink;
import zipkin.internal.DependencyLinker;
import zipkin.internal.Pair;
import zipkin.internal.Util;
import zipkin.internal.v2.Call;
import zipkin.internal.v2.Span;

import static zipkin.internal.Util.sortedList;

/**
 * Test storage component that keeps all spans in memory, accepting them on the calling thread.
 *
 * <p>Internally, spans are indexed on 64-bit trace ID
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
public final class InMemoryStorage implements SpanConsumer, SpanStore {

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    boolean strictTraceId = true;
    int maxSpanCount = 500000;

    /** {@inheritDoc} */
    public Builder strictTraceId(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
      return this;
    }

    /** Eldest traces are removed to ensure spans in memory don't exceed this value */
    public Builder maxSpanCount(int maxSpanCount) {
      if (maxSpanCount <= 0) throw new IllegalArgumentException("maxSpanCount <= 0");
      this.maxSpanCount = maxSpanCount;
      return this;
    }

    public InMemoryStorage build() {
      return new InMemoryStorage(this);
    }
  }

  /**
   * Primary source of data is this map, which includes spans ordered descending by timestamp. All
   * other maps are derived from the span values here. This uses a list for the spans, so that it is
   * visible (via /api/v2/trace/id?raw) when instrumentation report the same spans multiple times.
   */
  private final SortedMultimap<Pair<Long>, Span> spansByTraceIdTimeStamp =
    new SortedMultimap(VALUE_2_DESCENDING) {
      @Override Collection<Span> valueContainer() {
        return new LinkedList<>();
      }
    };

  /** This supports span lookup by {@link Span#traceId lower 64-bits of the trace ID} */
  private final SortedMultimap<Long, Pair<Long>> traceIdToTraceIdTimeStamps =
    new SortedMultimap<Long, Pair<Long>>(Long::compareTo) {
      @Override Collection<Pair<Long>> valueContainer() {
        return new LinkedHashSet<>();
      }
    };
  /** This is an index of {@link Span#traceId} by {@link zipkin.Endpoint#serviceName service name} */
  private final ServiceNameToTraceIds serviceToTraceIds = new ServiceNameToTraceIds();
  /** This is an index of {@link Span#name} by {@link zipkin.Endpoint#serviceName service name} */
  private final SortedMultimap<String, String> serviceToSpanNames =
    new SortedMultimap<String, String>(String::compareTo) {
      @Override Collection<String> valueContainer() {
        return new LinkedHashSet<>();
      }
    };

  final boolean strictTraceId;
  final int maxSpanCount;
  volatile int acceptedSpanCount;

  InMemoryStorage(Builder builder) {
    this.strictTraceId = builder.strictTraceId;
    this.maxSpanCount = builder.maxSpanCount;
  }

  public synchronized void clear() {
    acceptedSpanCount = 0;
    traceIdToTraceIdTimeStamps.clear();
    spansByTraceIdTimeStamp.clear();
    serviceToTraceIds.clear();
    serviceToSpanNames.clear();
  }

  @Override synchronized public Call<Void> accept(List<Span> spans) {
    int delta = spans.size();
    int spansToRecover = (spansByTraceIdTimeStamp.size() + delta) - maxSpanCount;
    evictToRecoverSpans(spansToRecover);
    for (Span span : spans) {
      Long timestamp = span.timestamp() != null ? span.timestamp() : Long.MIN_VALUE;
      long traceId = Util.lowerHexToUnsignedLong(span.traceId());
      Pair<Long> traceIdTimeStamp = Pair.create(traceId, timestamp);
      spansByTraceIdTimeStamp.put(traceIdTimeStamp, span);
      traceIdToTraceIdTimeStamps.put(traceId, traceIdTimeStamp);
      acceptedSpanCount++;

      String spanName = span.name();
      if (span.localServiceName() != null) {
        serviceToTraceIds.put(span.localServiceName(), traceId);
        serviceToSpanNames.put(span.localServiceName(), spanName);
      }
      if (span.remoteServiceName() != null) {
        serviceToTraceIds.put(span.remoteServiceName(), traceId);
        serviceToSpanNames.put(span.remoteServiceName(), spanName);
      }
    }
    return Call.create(null /* Void == null */);
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

  @Override
  public synchronized Call<List<List<Span>>> getTraces(QueryRequest request) {
    return getTraces(request, strictTraceId);
  }

  synchronized Call<List<List<Span>>> getTraces(QueryRequest request, boolean strictTraceId) {
    Set<Long> traceIdsInTimerange = traceIdsDescendingByTimestamp(request);
    if (traceIdsInTimerange.isEmpty()) return Call.emptyList();

    List<List<Span>> result = new ArrayList<>();
    for (Iterator<Long> traceId = traceIdsInTimerange.iterator();
      traceId.hasNext() && result.size() < request.limit(); ) {
      List<Span> next = spansByTraceId(traceId.next());
      if (!request.test(next)) continue;
      if (!strictTraceId) {
        result.add(next);
        continue;
      }

      // re-run the query as now spans are strictly grouped
      for (List<Span> strictTrace : strictByTraceId(next)) {
        if (request.test(strictTrace)) result.add(strictTrace);
      }
    }

    return Call.create(result);
  }

  static Collection<List<Span>> strictByTraceId(List<Span> next) {
    Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<>();
    for (Span span : next) {
      String traceId = span.traceId();
      if (!groupedByTraceId.containsKey(traceId)) {
        groupedByTraceId.put(traceId, new LinkedList<>());
      }
      groupedByTraceId.get(traceId).add(span);
    }
    return groupedByTraceId.values();
  }

  /** Used for testing. Returns all traces unconditionally. */
  public synchronized List<List<Span>> getTraces() {
    List<List<Span>> result = new ArrayList<>();
    for (long traceId : traceIdToTraceIdTimeStamps.keySet()) {
      List<Span> sameTraceId = spansByTraceId(traceId);
      if (strictTraceId) {
        result.addAll(strictByTraceId(sameTraceId));
      } else {
        result.add(sameTraceId);
      }
    }
    return result;
  }

  Set<Long> traceIdsDescendingByTimestamp(QueryRequest request) {
    Collection<Pair<Long>> traceIdTimestamps = request.serviceName() != null
      ? traceIdTimestampsByServiceName(request.serviceName())
      : spansByTraceIdTimeStamp.keySet();

    long endTs = request.endTs() * 1000;
    long startTs = endTs - request.lookback() * 1000;

    if (traceIdTimestamps == null || traceIdTimestamps.isEmpty()) return Collections.emptySet();
    Set<Long> result = new LinkedHashSet<>();
    for (Pair<Long> traceIdTimestamp : traceIdTimestamps) {
      if (traceIdTimestamp._2 >= startTs || traceIdTimestamp._2 <= endTs) {
        result.add(traceIdTimestamp._1);
      }
    }
    return result;
  }

  @Override public synchronized Call<List<Span>> getTrace(String traceId) {
    traceId = Span.normalizeTraceId(traceId);
    List<Span> spans = spansByTraceId(Util.lowerHexToUnsignedLong(traceId));
    if (spans == null || spans.isEmpty()) return Call.emptyList();
    if (!strictTraceId) return Call.create(spans);

    List<Span> filtered = new ArrayList<>(spans);
    Iterator<Span> iterator = filtered.iterator();
    while (iterator.hasNext()) {
      if (!iterator.next().traceId().equals(traceId)) {
        iterator.remove();
      }
    }
    return Call.create(filtered);
  }

  @Override public synchronized Call<List<String>> getServiceNames() {
    List<String> result = sortedList(serviceToTraceIds.keySet());
    return Call.create(result);
  }

  @Override public synchronized Call<List<String>> getSpanNames(String service) {
    if (service.isEmpty()) return Call.emptyList();
    service = service.toLowerCase(Locale.ROOT); // service names are always lowercase!
    List<String> result = sortedList(serviceToSpanNames.get(service));
    return Call.create(result);
  }

  @Override
  public synchronized Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    QueryRequest request = QueryRequest.newBuilder()
      .endTs(endTs)
      .lookback(lookback)
      .limit(Integer.MAX_VALUE).build();

    // We don't have a query parameter for strictTraceId when fetching dependency links, so we
    // ignore traceIdHigh. Otherwise, a single trace can appear as two, doubling callCount.
    Call<List<List<Span>>> getTracesCall = getTraces(request, false);
    return getTracesCall.map(traces -> {
      DependencyLinker linksBuilder = new DependencyLinker();
      for (Collection<Span> trace : traces) {
        // use a hash set to dedupe any redundantly accepted spans
        linksBuilder.putTrace(new LinkedHashSet<>(trace).iterator());
      }
      return linksBuilder.link();
    });
  }

  static final Comparator<Pair<Long>> VALUE_2_DESCENDING = (left, right) -> {
    int result = right._2.compareTo(left._2);
    if (result != 0) return result;
    return right._1.compareTo(left._1);
  };

  static final class ServiceNameToTraceIds extends SortedMultimap<String, Long> {
    ServiceNameToTraceIds() {
      super(String::compareTo);
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

  // Not synchronized as every exposed method on the enclosing type is
  static abstract class SortedMultimap<K, V> {
    final SortedMap<K, Collection<V>> delegate;
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

  private List<Span> spansByTraceId(long lowTraceId) {
    List<Span> sameTraceId = new ArrayList<>();
    for (Pair<Long> traceIdTimestamp : traceIdToTraceIdTimeStamps.get(lowTraceId)) {
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
