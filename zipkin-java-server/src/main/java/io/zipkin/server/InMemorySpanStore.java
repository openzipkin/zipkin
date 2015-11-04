/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.server;

import io.zipkin.BinaryAnnotation;
import io.zipkin.DependencyLink;
import io.zipkin.QueryRequest;
import io.zipkin.Span;
import io.zipkin.SpanStore;
import io.zipkin.internal.Nullable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.zipkin.internal.Util.merge;
import static io.zipkin.internal.Util.sortedList;

// TODO: make this not use Java 8, so we can put it into the core jar.
public final class InMemorySpanStore implements SpanStore {
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final Multimap<Long, Span> traceIdToSpans = new Multimap<>(LinkedList::new);
  private final Multimap<String, Long> serviceToTraceIds = new Multimap<>(LinkedHashSet::new);
  private final Multimap<String, String> serviceToSpanNames = new Multimap<>(LinkedHashSet::new);

  @Override
  public synchronized void accept(List<Span> spans) {
    spans.forEach(span -> {
      long traceId = span.traceId;
      traceIdToSpans.put(span.traceId, span);
      span.annotations.stream().filter(a -> a.endpoint != null)
          .map(annotation -> annotation.endpoint.serviceName)
          .forEach(serviceName -> {
            serviceToTraceIds.put(serviceName, traceId);
            serviceToSpanNames.put(serviceName, span.name);
          });
    });
  }

  synchronized void clear() {
    traceIdToSpans.clear();
    serviceToTraceIds.clear();
  }

  @Override
  public synchronized List<List<Span>> getTraces(QueryRequest request) {
    Collection<Long> traceIds = serviceToTraceIds.get(request.serviceName);
    if (traceIds == null || traceIds.isEmpty()) return Collections.emptyList();

    long endTs = (request.endTs > 0 && request.endTs != Long.MAX_VALUE) ? request.endTs
        : System.currentTimeMillis() / 1000;

    return toSortedTraces(traceIds.stream().map(traceIdToSpans::get)).stream()
        .filter(t -> t.stream().allMatch(s -> s.timestamp() <= endTs))
        .filter(spansPredicate(request))
        .limit(request.limit).collect(Collectors.toList());
  }

  @Override
  public synchronized List<List<Span>> getTracesByIds(List<Long> traceIds) {
    if (traceIds.isEmpty()) return Collections.emptyList();
    return toSortedTraces(traceIds.stream().map(traceIdToSpans::get));
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
  public List<DependencyLink> getDependencies(@Nullable Long startTs, @Nullable Long endTs) {
    return Collections.emptyList();
  }

  @Override
  public void close() {
  }

  static Predicate<List<Span>> spansPredicate(QueryRequest request) {
    return spans -> {
      Set<String> serviceNames = new LinkedHashSet<>();
      String spanName = request.spanName;
      Set<String> annotations = new LinkedHashSet<>(request.annotations);
      Map<String, String> binaryAnnotations = new LinkedHashMap<>(request.binaryAnnotations);

      for (Span span : spans) {
        span.annotations.forEach(a -> {
          annotations.remove(a.value);
          if (a.endpoint != null) {
            serviceNames.add(a.endpoint.serviceName);
          }
        });

        span.binaryAnnotations.forEach(b -> {
          if (b.type == BinaryAnnotation.Type.STRING && binaryAnnotations.containsKey(b.key)) {
            binaryAnnotations.remove(b.key, new String(b.value, UTF_8));
          }
          if (b.endpoint != null) {
            serviceNames.add(b.endpoint.serviceName);
          }
        });

        if (span.name.equals(spanName)) {
          spanName = null;
        }
      }
      return serviceNames.contains(request.serviceName) && spanName == null && annotations.isEmpty() && binaryAnnotations.isEmpty();
    };
  }

  static List<List<Span>> toSortedTraces(Stream<Collection<Span>> unfiltered) {
    return unfiltered.filter(spans -> spans != null && !spans.isEmpty())
        .map(spans -> merge(spans))
        .sorted((left, right) -> left.get(0).compareTo(right.get(0)))
        .collect(Collectors.toList());
  }

  static final class Multimap<K, V> {
    private final Map<K, Collection<V>> delegate = new LinkedHashMap<>();
    private final Supplier<Collection<V>> collectionFunction;

    public Multimap(Supplier<Collection<V>> collectionFunction) {
      this.collectionFunction = collectionFunction;
    }

    public Set<K> keySet() {
      return delegate.keySet();
    }

    void put(K key, V value) {
      delegate.computeIfAbsent(key, (k) -> collectionFunction.get()).add(value);
    }

    void clear() {
      delegate.clear();
    }

    Collection<V> get(K key) {
      return delegate.get(key);
    }
  }
}
