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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import zipkin.storage.Callback;

public final class CallbackCaptor<V> implements Callback<V> {
  // countDown + ref as BlockingQueue forbids null
  final CountDownLatch countDown = new CountDownLatch(1);
  final AtomicReference<Object> ref = new AtomicReference<>();

  /**
   * Blocks until {@link Callback#onSuccess(Object)} or {@link Callback#onError(Throwable)}.
   *
   * <p>Returns the successful value if {@link Callback#onSuccess(Object)} was called. <p>Throws if
   * {@link Callback#onError(Throwable)} was called.
   */
  @Nullable public V get() {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          countDown.await();
          Object result = ref.get();
          if (result instanceof Throwable) {
            if (result instanceof Error) throw (Error) result;
            if (result instanceof RuntimeException) throw (RuntimeException) result;
            throw new RuntimeException((Exception) result);
          }
          return (V) result;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override public void onSuccess(@Nullable V value) {
    ref.set(value);
    countDown.countDown();
  }

  @Override public void onError(Throwable t) {
    ref.set(t);
    countDown.countDown();
  }
}
