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
package zipkin.storage;

import java.util.List;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.Nullable;

public interface SpanStore {

  /**
   * Get the available trace information from the storage system. Spans in trace are sorted by the
   * first annotation timestamp in that span. First event should be first in the spans list.
   *
   * <p> Results are sorted in order of the first span's timestamp, and contain up to {@link
   * QueryRequest#limit} elements.
   *
   * <p>If {@link StorageComponent.Builder#strictTraceId(boolean)} is enabled, spans with the
   * same 64-bit trace ID will be grouped together.
   */
  List<List<Span>> getTraces(QueryRequest request);

  /**
   * Get the available trace information from the storage system. Spans in trace are sorted by the
   * first annotation timestamp in that span. First event should be first in the spans list.
   *
   * <p>When {@link StorageComponent.Builder#strictTraceId(boolean)} is true, spans with the
   * same {@code traceIdLow} are returned even if the {@code traceIdHigh is different}.
   *
   * @param traceIdHigh The upper 64-bits of the trace ID. See {@link Span#traceIdHigh}
   * @param traceIdLow The lower 64-bits of the trace ID. See {@link Span#traceId}
   * @return a list of spans with the same 128-bit trace ID, or null if not present.
   * @since 1.15
   */
  @Nullable
  List<Span> getTrace(long traceIdHigh, long traceIdLow);

  /**
   * Retrieves spans that share a 128-bit trace id, as returned from backend data store queries,
   * with no ordering expectation.
   *
   * <p>This is different, but related to {@link #getTrace}. {@link #getTrace} cleans data by
   * merging spans, adding timestamps and performing clock skew adjustment. This feature is for
   * debugging zipkin logic or zipkin instrumentation.
   *
   * <p>When {@link StorageComponent.Builder#strictTraceId(boolean)} is true, spans with the
   * same {@code traceIdLow} are returned even if the {@code traceIdHigh is different}.
   *
   * @param traceIdHigh The upper 64-bits of the trace ID. See {@link Span#traceIdHigh}
   * @param traceIdLow The lower 64-bits of the trace ID. See {@link Span#traceId}
   * @return a list of spans with the same 128-bit trace ID, or null if not present.
   * @since 1.15
   */
  @Nullable
  List<Span> getRawTrace(long traceIdHigh, long traceIdLow);

  /**
   * This calls {@link #getTrace(long, long)} with {@code traceIdHigh} set to zero.
   *
   * @deprecated Please switch to {@link #getTrace(long, long)}
   */
  @Deprecated
  @Nullable
  List<Span> getTrace(long traceId);

  /**
   * This calls {@link #getRawTrace(long, long)} with {@code traceIdHigh} set to zero.
   *
   * @deprecated Please switch to {@link #getRawTrace(long, long)}
   */
  @Deprecated
  @Nullable
  List<Span> getRawTrace(long traceId);

  /**
   * Get all the {@link Endpoint#serviceName service names}.
   *
   * <p> Results are sorted lexicographically
   */
  List<String> getServiceNames();

  /**
   * Get all the span names for a particular {@link Endpoint#serviceName}.
   *
   * <p> Results are sorted lexicographically
   */
  List<String> getSpanNames(String serviceName);

  /**
   * Returns dependency links derived from spans.
   *
   * <p>Implementations may bucket aggregated data, for example daily. When this is the case, endTs
   * may be floored to align with that bucket, for example midnight if daily. lookback applies to
   * the original endTs, even when bucketed. Using the daily example, if endTs was 11pm and lookback
   * was 25 hours, the implementation would query against 2 buckets.
   *
   * <p>Some implementations parse {@link zipkin2.Span} from storage and call
   * {@link zipkin.internal.DependencyLinker} to aggregate links. The reason is certain graph logic,
   * such as skipping up the tree is difficult to implement as a storage query.
   *
   * <p>There's no parameter to indicate how to handle mixed ID length: this operates the same as if
   * {@link StorageComponent.Builder#strictTraceId(boolean)} was set to false. This ensures call
   * counts are not incremented twice due to one hop downgrading from 128 to 64-bit trace IDs.
   *
   * @param endTs only return links from spans where {@link Span#timestamp} are at or before this
   * time in epoch milliseconds.
   * @param lookback only return links from spans where {@link Span#timestamp} are at or after
   * (endTs - lookback) in milliseconds. Defaults to endTs.
   * @return dependency links in an interval contained by (endTs - lookback) or empty if none are
   * found
   */
  List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback);
}
