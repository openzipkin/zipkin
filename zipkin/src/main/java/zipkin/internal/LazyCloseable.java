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
package zipkin.internal;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This is a special-cased lazy with the following behaviors to support potentially expensive, I/O
 * computations.
 *
 * <pre>
 * <ul>
 *   <li>Only closes computed values: this avoids accidentally causing I/O to close something
 * never used.</li>
 *   <li>Remembers exceptions for a second: this avoids repeated computation attempts, as doing so
 * could harm the process or the backend</li>
 * </ul>
 * </pre>
 */
public abstract class LazyCloseable<T> extends Lazy<T> implements Closeable {

  /** How long to cache an exception computing a value */
  final long exceptionExpirationDuration = TimeUnit.SECONDS.toNanos(1);
  // the below fields are guarded by this, and visible due to writes inside a synchronized block
  RuntimeException lastException;
  long exceptionExpiration;

  @Override T tryCompute() {
    // if last attempt was an exception, and we are within an cache interval, throw.
    if (lastException != null) {
      if (exceptionExpiration - nanoTime() <= 0) {
        lastException = null;
      } else {
        throw lastException;
      }
    }
    try {
      return compute();
    } catch (RuntimeException e) {
      // this attempt failed. Remember the exception so that we can throw it.
      lastException = e;
      exceptionExpiration = nanoTime() + exceptionExpirationDuration;
      throw e;
    }
  }

  // visible for testing, since nanoTime is weird and can return negative
  long nanoTime() {
    return System.nanoTime();
  }

  @Override
  public void close() throws IOException {
    T maybeNull = maybeNull();
    if (maybeNull != null) {
      if (!(maybeNull instanceof Closeable)) {
        throw new IllegalArgumentException("Override close() to close " + maybeNull);
      }
      ((Closeable) maybeNull).close();
    }
  }

  /** Used to conditionally close resources. No-op if the value hasn't been computed, yet. */
  protected final T maybeNull() {
    return instance;
  }
}
