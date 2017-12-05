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
package zipkin.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zipkin.Span;

public final class GroupByTraceId {

  public static final Comparator<List<Span>> TRACE_DESCENDING = new Comparator<List<Span>>() {
    @Override public int compare(List<Span> left, List<Span> right) {
      return right.get(0).compareTo(left.get(0));
    }
  };

  public static List<List<Span>> apply(Collection<Span> input, boolean strictTraceId,
      boolean adjust) {
    if (input == null || input.isEmpty()) return Collections.emptyList();

    Map<Pair<Long>, List<Span>> groupedByTraceId = new LinkedHashMap<>();
    for (Span span : input) {
      Pair<Long> traceId = Pair.create(strictTraceId ? span.traceIdHigh : 0L, span.traceId);
      if (!groupedByTraceId.containsKey(traceId)) {
        groupedByTraceId.put(traceId, new ArrayList<>());
      }
      groupedByTraceId.get(traceId).add(span);
    }

    List<List<Span>> result = new ArrayList<>(groupedByTraceId.size());
    for (List<Span> sameTraceId : groupedByTraceId.values()) {
      result.add(adjust ? CorrectForClockSkew.apply(MergeById.apply(sameTraceId)) : sameTraceId);
    }
    Collections.sort(result, TRACE_DESCENDING);
    return result;
  }
}
