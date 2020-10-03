/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import zipkin2.Call.Mapper;
import zipkin2.Span;
import zipkin2.internal.FilterTraces;

/**
 * Storage implementation often need to re-check query results when {@link
 * StorageComponent.Builder#strictTraceId(boolean) strict trace ID} is disabled.
 */
public final class StrictTraceId {

  public static Mapper<List<Span>, List<Span>> filterSpans(String traceId) {
    return new FilterSpans(traceId);
  }

  /**
   * Filters the mutable input client-side when there's a clash on lower 64-bits of a trace ID.
   *
   * @see FilterTraces
   */
  public static Mapper<List<List<Span>>, List<List<Span>>> filterTraces(QueryRequest request) {
    return new FilterTracesIfClashOnLowerTraceId(request);
  }

  static final class FilterTracesIfClashOnLowerTraceId
    implements Mapper<List<List<Span>>, List<List<Span>>> {
    final QueryRequest request;

    FilterTracesIfClashOnLowerTraceId(QueryRequest request) {
      this.request = request;
    }

    @Override public List<List<Span>> map(List<List<Span>> input) {
      if (hasClashOnLowerTraceId(input)) {
        return FilterTraces.create(request).map(input);
      }
      return input;
    }

    @Override public String toString() {
      return "FilterTracesIfClashOnLowerTraceId{request=" + request + "}";
    }
  }

  /** Returns true if any trace clashes on the right-most 16 characters of the trace ID */
  // Concretely, Netflix have a special index template for a multi-tag, "fit.sessionId". If we
  // blindly filtered without seeing if we had to, a match that works on the server side would
  // fail client side. Normally, we wouldn't special case like this, but not filtering unless
  // necessary is also more efficient.
  static boolean hasClashOnLowerTraceId(List<List<Span>> input) {
    int traceCount = input.size();
    if (traceCount <= 1) return false;

    // NOTE: It is probably more efficient to do clever sorting and peeking here, but the call site
    // is query side, which is not in the critical path of user code. A set is much easier to grok.
    Set<String> traceIdLows = new LinkedHashSet<String>();
    boolean clash = false;
    for (int i = 0; i < traceCount; i++) {
      String traceId = lowerTraceId(input.get(i).get(0).traceId());
      if (!traceIdLows.add(traceId)) {
        clash = true;
        break;
      }
    }
    return clash;
  }

  static String lowerTraceId(String traceId) {
    return traceId.length() == 16 ? traceId : traceId.substring(16);
  }

  static final class FilterSpans implements Mapper<List<Span>, List<Span>> {
    final String traceId;

    FilterSpans(String traceId) {
      this.traceId = traceId;
    }

    @Override public List<Span> map(List<Span> input) {
      Iterator<Span> i = input.iterator();
      while (i.hasNext()) { // Not using removeIf as that's java 8+
        Span next = i.next();
        if (!next.traceId().equals(traceId)) i.remove();
      }
      return input;
    }

    @Override public String toString() {
      return "FilterSpans{traceId=" + traceId + "}";
    }
  }

  /**
   * Returns a function that filters its mutable input when it contains a trace not matching the
   * specified trace IDs.
   *
   * <p>Make sure the input IDs are unique and {@link Span#normalizeTraceId(String) normalized}.
   */
  public static Mapper<List<List<Span>>, List<List<Span>>> filterTraces(Iterable<String> traceIds) {
    return new FilterTracesByIds(traceIds);
  }

  static final class FilterTracesByIds implements Mapper<List<List<Span>>, List<List<Span>>> {
    final Set<String> traceIds;

    FilterTracesByIds(Iterable<String> sanitizedIds) {
      traceIds = new LinkedHashSet<String>();
      for (String traceId : sanitizedIds) {
        traceIds.add(traceId);
      }
    }

    @Override
    public List<List<Span>> map(List<List<Span>> input) {
      Iterator<List<Span>> i = input.iterator();
      while (i.hasNext()) { // Not using removeIf as that's java 8+
        List<Span> next = i.next();
        if (!traceIds.contains(next.get(0).traceId())) {
          i.remove();
        }
      }
      return input;
    }

    @Override
    public String toString() {
      return "FilterTracesByIds{traceIds=" + traceIds + "}";
    }
  }

  StrictTraceId() {
  }
}
