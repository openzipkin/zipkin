/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.ArrayList;
import java.util.List;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

public final class FilterTraces implements Call.Mapper<List<List<Span>>, List<List<Span>>> {
  /** Filters the mutable input based on the query */
  public static Call.Mapper<List<List<Span>>, List<List<Span>>> create(QueryRequest request) {
    return new FilterTraces(request);
  }

  final QueryRequest request;

  FilterTraces(QueryRequest request) {
    this.request = request;
  }

  @Override public List<List<Span>> map(List<List<Span>> input) {
    int length = input.size();
    if (length == 0) return input;
    ArrayList<List<Span>> result = new ArrayList<>(length);
    for (List<Span> next : input) {
      if (request.test(next)) result.add(next);
    }
    return result;
  }

  @Override public String toString() {
    return "FilterTraces{request=" + request + "}";
  }
}
