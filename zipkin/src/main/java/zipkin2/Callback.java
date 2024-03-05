/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2;

import zipkin2.internal.Nullable;

/**
 * A callback of a single result or error.
 *
 * <p>This is a bridge to async libraries such as CompletableFuture complete, completeExceptionally.
 *
 * <p>Implementations will call either {@link #onSuccess} or {@link #onError}, but not both.
 */
public interface Callback<V> {

  /**
   * Invoked when computation produces its potentially null value successfully.
   *
   * <p>When this is called, {@link #onError} won't be.
   */
  void onSuccess(@Nullable V value);

  /**
   * Invoked when computation produces a possibly null value successfully.
   *
   * <p>When this is called, {@link #onSuccess} won't be.
   */
  void onError(Throwable t);
}
