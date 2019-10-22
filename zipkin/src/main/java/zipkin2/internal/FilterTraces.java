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
    for (int i = 0; i < length; i++) {
      List<Span> next = input.get(i);
      if (request.test(next)) result.add(next);
    }
    return result;
  }

  @Override public String toString() {
    return "FilterTraces{request=" + request + "}";
  }
}
