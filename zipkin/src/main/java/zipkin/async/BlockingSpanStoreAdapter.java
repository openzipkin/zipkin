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
package zipkin.async;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.internal.Nullable;

/**
 * A {@link SpanStore} implementation that can take a {@link AsyncSpanStore} and call its methods
 * with blocking, for use in callers that need a normal {@link SpanStore}.
 */
public final class BlockingSpanStoreAdapter implements SpanStore {
  /**
   * Internal flag that allows you read-your-writes consistency during tests.
   *
   * <p>This is internal as collection endpoints are usually in different threads or not in the same
   * process as query ones. Special-casing this allows tests to pass without changing {@link
   * AsyncSpanConsumer#accept}.
   */
  public static boolean BLOCK_ON_ACCEPT;

  private final AsyncSpanStore delegate;

  public BlockingSpanStoreAdapter(AsyncSpanStore delegate) {
    this.delegate = delegate;
  }

  // Only method that does not actually block even in synchronous spanstores.
  @Override public void accept(List<Span> spans) {
    CallbackCaptor<Void> captor = new CallbackCaptor<>();
    delegate.accept(spans, captor);
    if (BLOCK_ON_ACCEPT) {
      captor.get();
    }
  }

  @Override public List<List<Span>> getTraces(QueryRequest request) {
    CallbackCaptor<List<List<Span>>> captor = new CallbackCaptor<>();
    delegate.getTraces(request, captor);
    return captor.get();
  }

  @Override public List<Span> getTrace(long id) {
    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    delegate.getTrace(id, captor);
    return captor.get();
  }

  @Override public List<Span> getRawTrace(long traceId) {
    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    delegate.getRawTrace(traceId, captor);
    return captor.get();
  }

  @Override public List<String> getServiceNames() {
    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    delegate.getServiceNames(captor);
    return captor.get();
  }

  @Override public List<String> getSpanNames(String serviceName) {
    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    delegate.getSpanNames(serviceName, captor);
    return captor.get();
  }

  @Override public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    CallbackCaptor<List<DependencyLink>> captor = new CallbackCaptor<>();
    delegate.getDependencies(endTs, lookback, captor);
    return captor.get();
  }

  static final class CallbackCaptor<V> implements Callback<V> {
    // countDown + ref as BlockingQueue forbids null
    CountDownLatch countDown = new CountDownLatch(1);
    AtomicReference<Object> ref = new AtomicReference<>();

    /**
     * Blocks until {@link Callback#onSuccess(Object)} or {@link Callback#onError(Throwable)}.
     *
     * <p>Returns the successful value if {@link Callback#onSuccess(Object)} was called. <p>Throws
     * if {@link Callback#onError(Throwable)} was called.
     */
    @Nullable V get() {
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
}
