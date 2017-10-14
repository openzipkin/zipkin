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

import zipkin.internal.Nullable;

/**
 * A callback of a single result or error.
 *
 * <p>This is a bridge to async libraries such as CompletableFuture complete, completeExceptionally.
 *
 * <p>Implementations will call either {@link #onSuccess} or {@link #onError}, but not both.
 */
public interface Callback<V> {

  Callback<Void> NOOP = new Callback<Void>() {
    @Override public void onSuccess(@Nullable Void value) {
    }

    @Override public void onError(Throwable t) {
    }
  };

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
