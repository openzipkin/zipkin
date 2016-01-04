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
package io.zipkin.server;

import io.zipkin.BinaryAnnotation;
import io.zipkin.DependencyLink;
import io.zipkin.QueryRequest;
import io.zipkin.Span;
import io.zipkin.SpanStore;
import io.zipkin.internal.ApplyTimestampAndDuration;
import io.zipkin.internal.CorrectForClockSkew;
import io.zipkin.internal.MergeById;
import io.zipkin.internal.Nullable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.zipkin.internal.Util.sortedList;
import static java.util.stream.Collectors.toList;

// TODO: make this not use Java 8, so we can put it into the core jar.
public final class InMemorySpanStore implements SpanStore {
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final Multimap<Long, Span> traceIdToSpans = new Multimap<>(LinkedList::new);
  private final Multimap<String, Long> serviceToTraceIds = new Multimap<>(LinkedHashSet::new);
  private final Multimap<String, String> serviceToSpanNames = new Multimap<>(LinkedHashSet::new);

  @Override
  public synchronized void accept(Iterator<Span> spans) {
    while (spans.hasNext()) {
      Span span = ApplyTimestampAndDuration.apply(spans.next());
      long traceId = span.traceId;
      String spanName = span.name;
      traceIdToSpans.put(span.traceId, span);
      Stream.concat(span.annotations.stream().map(a -> a.endpoint),
                    span.binaryAnnotations.stream().map(a -> a.endpoint))
          .filter(e -> e != null && !e.serviceName.isEmpty())
          .map(e -> e.serviceName)
          .distinct()
          .forEach(serviceName -> {
            serviceToTraceIds.put(serviceName, traceId);
            serviceToSpanNames.put(serviceName, spanName);
          });
    }
  }

  synchronized void clear() {
    traceIdToSpans.clear();
    serviceToTraceIds.clear();
  }

  @Override
  public synchronized List<List<Span>> getTraces(QueryRequest request) {
    Collection<Long> traceIds = serviceToTraceIds.get(request.serviceName);
    if (traceIds == null || traceIds.isEmpty()) return Collections.emptyList();

    return toSortedTraces(traceIds.stream().map(traceIdToSpans::get)).stream()
            .filter(spansPredicate(request))
            .limit(request.limit).collect(toList());
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
  public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    return Collections.emptyList();
  }

  @Override
  public void close() {
  }

  private static Predicate<List<Span>> spansPredicate(QueryRequest request) {
    return spans -> {
      Long timestamp = spans.get(0).timestamp;
      if (timestamp == null ||
          timestamp < (request.endTs - request.lookback) * 1000 ||
          timestamp > request.endTs * 1000) {
        return false;
      }
      Set<String> serviceNames = new LinkedHashSet<>();
      Predicate<Long> durationPredicate = null;
      if (request.minDuration != null && request.maxDuration != null) {
        durationPredicate = d -> d >= request.minDuration && d <= request.maxDuration;
      } else if (request.minDuration != null){
        durationPredicate = d -> d >= request.minDuration;
      }
      String spanName = request.spanName;
      Set<String> annotations = new LinkedHashSet<>(request.annotations);
      Map<String, String> binaryAnnotations = new LinkedHashMap<>(request.binaryAnnotations);

      Set<String> currentServiceNames = new LinkedHashSet<>();
      for (Span span : spans) {
        currentServiceNames.clear();

        span.annotations.forEach(a -> {
          annotations.remove(a.value);
          if (a.endpoint != null) {
            serviceNames.add(a.endpoint.serviceName);
            currentServiceNames.add(a.endpoint.serviceName);
          }
        });

        span.binaryAnnotations.forEach(b -> {
          if (b.type == BinaryAnnotation.Type.STRING && binaryAnnotations.containsKey(b.key)) {
            binaryAnnotations.remove(b.key, new String(b.value, UTF_8));
          }
          if (b.endpoint != null) {
            serviceNames.add(b.endpoint.serviceName);
            currentServiceNames.add(b.endpoint.serviceName);
          }
        });

        if (currentServiceNames.contains(request.serviceName) && durationPredicate != null) {
          if (durationPredicate.test(span.duration)) {
            durationPredicate = null;
          }
        }

        if (span.name.equals(spanName)) {
          spanName = null;
        }
      }
      return serviceNames.contains(request.serviceName)
          && spanName == null
          && annotations.isEmpty()
          && binaryAnnotations.isEmpty()
          && durationPredicate == null;
    };
  }

  private static List<List<Span>> toSortedTraces(Stream<Collection<Span>> unfiltered) {
    return unfiltered.filter(spans -> spans != null && !spans.isEmpty())
        .map(MergeById::apply)
        .map(CorrectForClockSkew::apply)
        .sorted((left, right) -> right.get(0).compareTo(left.get(0)))
        .collect(toList());
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
