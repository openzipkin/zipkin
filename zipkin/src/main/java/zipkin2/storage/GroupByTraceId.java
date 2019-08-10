/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
    return Collections.unmodifiableList(new ArrayList<>(groupedByTraceId.values()));
  }

  @Override public String toString() {
    return "GroupByTraceId{strictTraceId=" + strictTraceId + "}";
  }
}
