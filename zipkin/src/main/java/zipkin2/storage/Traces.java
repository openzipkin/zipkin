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

import java.util.List;
import zipkin2.Call;
import zipkin2.Span;

/**
 * Allows readback of traces by ID, as written by a {@link SpanConsumer}.
 *
 * <p>Specifically, this provides apis present when {@link StorageComponent.Builder#searchEnabled(boolean)
 * search is disabled}.
 *
 * <p>Note: This is not considered a user-level Api, rather an Spi that can be used to bind
 * user-level abstractions such as futures or observables.
 *
 * @since 2.17
 */
public interface Traces {
  /**
   * Retrieves spans that share a 128-bit trace id with no ordering expectation or empty if none are
   * found.
   *
   * <p>When strict trace ID is disabled, spans with the same right-most 16 characters are returned
   * even if the characters to the left are not.
   *
   * <p>Implementations should use {@link Span#normalizeTraceId(String)} to ensure consistency.
   *
   * @param traceId the {@link Span#traceId() trace ID}
   */
  Call<List<Span>> getTrace(String traceId);

  /**
   * Retrieves any traces with the specified IDs. Results return in any order, and can be empty.
   *
   * <p>When strict trace ID is disabled, spans with the same right-most 16 characters are returned
   * even if the characters to the left are not.
   *
   * <p>Implementations should use {@link Span#normalizeTraceId(String)} on each input trace ID to
   * ensure consistency.
   *
   * @param traceIds a list of unique {@link Span#traceId() trace IDs}.
   * @return traces matching the supplied trace IDs, in any order
   */
  Call<List<List<Span>>> getTraces(Iterable<String> traceIds);
}
