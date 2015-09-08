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
package io.zipkin.query;

import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.Span;
import io.zipkin.Trace;
import io.zipkin.internal.Nullable;
import java.nio.charset.Charset;
import java.util.ArrayList;
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

public final class InMemoryZipkinQuery implements ZipkinQuery {
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final Multimap<Long, Span> traceIdToSpans = new Multimap<>(LinkedList::new);
  private final Multimap<String, Long> serviceToTraceIds = new Multimap<>(LinkedHashSet::new);
  private final Multimap<String, String> serviceToSpanNames = new Multimap<>(LinkedHashSet::new);

  public synchronized void accept(Iterable<Span> spans) {
    spans.forEach(span -> {
      long traceId = span.traceId();
      traceIdToSpans.put(span.traceId(), span);
      span.annotations().stream().filter(a -> a.host() != null)
          .map(annotation -> annotation.host().serviceName().toLowerCase())
          .forEach(serviceName -> {
            serviceToTraceIds.put(serviceName, traceId);
            serviceToSpanNames.put(serviceName, span.name());
          });
    });
  }

  synchronized void clear() {
    traceIdToSpans.clear();
    serviceToTraceIds.clear();
  }

  @Override
  public synchronized List<Trace> getTraces(QueryRequest request) {
    Collection<Long> traceIds = serviceToTraceIds.get(request.serviceName());
    if (traceIds == null || traceIds.isEmpty()) return Collections.emptyList();

    Predicate<Span> finalPredicate = spanPredicate(request);
    return traceIds.stream().map(traceIdToSpans::get)
        .filter(spans -> spans.stream().anyMatch(finalPredicate))
        .limit(request.limit())
        .map(spans -> Trace.create(new ArrayList<>(spans)))
        .collect(Collectors.toList());
  }

  @Override
  public synchronized List<Trace> getTracesByIds(List<Long> traceIds, boolean adjustClockSkew) {
    if (traceIds.isEmpty()) return Collections.emptyList();
    return traceIds.stream().map(traceIdToSpans::get)
        .filter(spans -> spans != null)
        .map(spans -> Trace.create(new ArrayList<>(spans)))
        .collect(Collectors.toList());
  }

  @Override
  public synchronized Set<String> getServiceNames() {
    return new LinkedHashSet<>(serviceToTraceIds.keySet());
  }

  @Override
  public synchronized Set<String> getSpanNames(@Nullable String service) {
    if (service == null) return Collections.emptySet();
    Collection<String> result = serviceToSpanNames.get(service);
    return result != null ? new LinkedHashSet<>(result) : Collections.emptySet();
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

  static Predicate<Span> spanPredicate(QueryRequest request) {
    long endTs = (request.endTs() > 0 && request.endTs() != Long.MAX_VALUE) ? request.endTs()
        : System.currentTimeMillis() / 1000;
    Predicate<Span> spanPredicate =
        s -> s.annotations().stream().map(Annotation::timestamp).allMatch(ts -> ts < endTs);
    if (request.spanName() != null) {
      spanPredicate = spanPredicate.and(s -> s.name().equals(request.spanName()));
    }
    if (request.annotations() != null && !request.annotations().isEmpty()) {
      spanPredicate = spanPredicate.and(s -> s.annotations().stream().map(
          Annotation::value).allMatch(v -> request.annotations().contains(v)));
    }
    if (request.binaryAnnotations() != null && !request.binaryAnnotations().isEmpty()) {
      spanPredicate = spanPredicate.and(s -> s.binaryAnnotations().stream()
          .filter(b -> b.type() == BinaryAnnotation.Type.STRING)
          .filter(b -> request.binaryAnnotations().containsKey(b.key()))
          .allMatch(
              b -> request.binaryAnnotations().get(b.key()).equals(new String(b.value(), UTF_8))));
    }
    return spanPredicate;
  }
}
