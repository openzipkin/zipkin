/**
 * Copyright 2015-2016 The OpenZipkin Authors
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

import zipkin.storage.StorageComponent;

/**
 * Memoizes the result of {@link #compute()}, used when {@link StorageComponent} needs to share a
 * stateful object that performs I/O in its constructor.
 */
public abstract class Lazy<T> {

  volatile T instance = null;

  /** Remembers the result, if the operation completed unexceptionally. */
  protected abstract T compute();

  /** Returns the same value, computing as necessary */
  public final T get() {
    T result = instance;
    if (result == null) {
      synchronized (this) {
        result = instance;
        if (result == null) {
          instance = result = tryCompute();
        }
      }
    }
    return result;
  }

  /**
   * This is called in a synchronized block when the value to memorize hasn't yet been computed.
   *
   * <p>Extracted only for LazyCloseable, hence package protection.
   */
  T tryCompute() {
    return compute();
  }
}
