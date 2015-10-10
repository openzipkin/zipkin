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
package io.zipkin;

import io.zipkin.internal.Nullable;
import java.io.Closeable;
import java.util.List;

public interface SpanStore extends Closeable {

  /**
   * Sinks the given spans, ignoring duplicate annotations.
   */
  void accept(List<Span> spans);

  /**
   * Get the available trace information from the storage system. Spans in trace are sorted by the
   * first annotation timestamp in that span. First event should be first in the spans list.
   *
   * <p/> Results are sorted in order of the first span's timestamp, and contain up to {@link
   * QueryRequest#limit} elements.
   */
  List<List<Span>> getTraces(QueryRequest request);

  /**
   * Get the available trace information from the storage system. Spans in trace are sorted by the
   * first annotation timestamp in that span. First event should be first in the spans list.
   *
   * <p/> Results are sorted in order of the first span's timestamp, and contain less elements than
   * trace IDs when corresponding traces aren't available.
   */
  List<List<Span>> getTracesByIds(List<Long> traceIds);

  /**
   * Get all the {@link Endpoint#serviceName service names}.
   *
   * <p/> Results are sorted lexicographically
   */
  List<String> getServiceNames();

  /**
   * Get all the span names for a particular {@link Endpoint#serviceName}.
   *
   * <p/> Results are sorted lexicographically
   */
  List<String> getSpanNames(String serviceName);

  /**
   * @param startTs microseconds from epoch, defaults to one day before end_time
   * @param endTs microseconds from epoch, defaults to now
   * @return dependency links in an interval contained by startTs and endTs, or empty if none are
   * found
   */
  List<DependencyLink> getDependencies(@Nullable Long startTs, @Nullable Long endTs);

  @Override
  void close();
}
