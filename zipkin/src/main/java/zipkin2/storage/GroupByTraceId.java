/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zipkin2.Call;
import zipkin2.Span;

import static zipkin2.storage.StrictTraceId.lowerTraceId;

/**
 * A mapper that groups unorganized input spans by trace ID. Useful when preparing a result for
 * {@link SpanStore#getTraces(QueryRequest)}.
 */
public final class GroupByTraceId implements Call.Mapper<List<Span>, List<List<Span>>> {
  public static Call.Mapper<List<Span>, List<List<Span>>> create(boolean strictTraceId) {
    return new GroupByTraceId(strictTraceId);
  }

  final boolean strictTraceId;

  GroupByTraceId(boolean strictTraceId) {
    this.strictTraceId = strictTraceId;
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  @Override public List<List<Span>> map(List<Span> input) {
    if (input.isEmpty()) return Collections.emptyList();

    Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<>();
    for (Span span : input) {
      String traceId = span.traceId();
      if (!strictTraceId) traceId = lowerTraceId(traceId);
      if (!groupedByTraceId.containsKey(traceId)) {
        groupedByTraceId.put(traceId, new ArrayList<>());
      }
      groupedByTraceId.get(traceId).add(span);
    }
    // Modifiable so that StrictTraceId can filter without allocating a new list
    return new ArrayList<>(groupedByTraceId.values());
  }

  @Override public String toString() {
    return "GroupByTraceId{strictTraceId=" + strictTraceId + "}";
  }
}
