/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
