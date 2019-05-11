/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal.throttle;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Limiter.Listener;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import zipkin2.Call;
import zipkin2.Callback;

/**
 * {@link Call} implementation that is backed by an {@link ExecutorService}. The ExecutorService
 * serves two purposes:
 * <ol>
 * <li>Limits the number of requests that can run in parallel.</li>
 * <li>Depending on configuration, can queue up requests to make sure we don't aggressively drop
 * requests that would otherwise succeed if given a moment. Bounded queues are safest for this as
 * unbounded ones can lead to heap exhaustion and {@link OutOfMemoryError OOM errors}.</li>
 * </ol>
 *
 * @see ThrottledStorageComponent
 */
final class ThrottledCall<V> extends Call.Base<V> {
  final ExecutorService executor;
  final Limiter<Void> limiter;
  final Call<V> delegate;

  ThrottledCall(ExecutorService executor, Limiter<Void> limiter, Call<V> delegate) {
    this.executor = executor;
    this.limiter = limiter;
    this.delegate = delegate;
  }

  @Override protected V doExecute() throws IOException {
    Listener limitListener = limiter.acquire(null).orElseThrow(RejectedExecutionException::new);

    try {
      // Make sure we throttle
      Future<V> future = executor.submit(() -> {
        String oldName = setCurrentThreadName(delegate.toString());
        try {
          return delegate.execute();
        } finally {
          setCurrentThreadName(oldName);
        }
      });
      V result = future.get(); // Still block for the response

      limitListener.onSuccess();
      return result;
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RejectedExecutionException) {
        // Storage rejected us, throttle back
        limitListener.onDropped();
      } else {
        limitListener.onIgnore();
      }

      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof IOException) {
        throw (IOException) cause;
      } else {
        throw new RuntimeException("Issue while executing on a throttled call", cause);
      }
    } catch (InterruptedException e) {
      limitListener.onIgnore();
      throw new RuntimeException("Interrupted while blocking on a throttled call", e);
    } catch (RuntimeException | Error e) {
      propagateIfFatal(e);
      // Ignoring in all cases here because storage itself isn't saying we need to throttle.  Though, we may still be
      // write bound, but a drop in concurrency won't necessarily help.
      limitListener.onIgnore();
      throw e;
    }
  }

  @Override protected void doEnqueue(Callback<V> callback) {
    Listener limitListener = limiter.acquire(null).orElseThrow(RejectedExecutionException::new);

    try {
      executor.execute(new QueuedCall<>(delegate, callback, limitListener));
    } catch (RuntimeException | Error e) {
      propagateIfFatal(e);
      // Ignoring in all cases here because storage itself isn't saying we need to throttle.  Though, we may still be
      // write bound, but a drop in concurrency won't necessarily help.
      limitListener.onIgnore();
      throw e;
    }
  }

  @Override public Call<V> clone() {
    return new ThrottledCall<>(executor, limiter, delegate.clone());
  }

  @Override public String toString() {
    return "Throttled(" + delegate + ")";
  }

  static String setCurrentThreadName(String name) {
    Thread thread = Thread.currentThread();
    String originalName = thread.getName();
    thread.setName(name);
    return originalName;
  }

  static final class QueuedCall<V> implements Runnable {
    final Call<V> delegate;
    final Callback<V> callback;
    final Listener limitListener;

    QueuedCall(Call<V> delegate, Callback<V> callback, Listener limitListener) {
      this.delegate = delegate;
      this.callback = callback;
      this.limitListener = limitListener;
    }

    @Override public void run() {
      try {
        if (delegate.isCanceled()) return;

        String oldName = setCurrentThreadName(delegate.toString());
        try {
          enqueueAndWait();
        } finally {
          setCurrentThreadName(oldName);
        }
      } catch (RuntimeException | Error e) {
        propagateIfFatal(e);
        limitListener.onIgnore();
        callback.onError(e);
      }
    }

    void enqueueAndWait() {
      ThrottledCallback<V> throttleCallback = new ThrottledCallback<>(callback, limitListener);
      delegate.enqueue(throttleCallback);

      // Need to wait here since the callback call will run asynchronously also.
      // This ensures we don't exceed our throttle/queue limits.
      throttleCallback.await();
    }

    @Override public String toString() {
      return "QueuedCall{delegate=" + delegate + ", callback=" + callback + "}";
    }
  }

  static final class ThrottledCallback<V> implements Callback<V> {
    final Callback<V> delegate;
    final Listener limitListener;
    final CountDownLatch latch = new CountDownLatch(1);

    ThrottledCallback(Callback<V> delegate, Listener limitListener) {
      this.delegate = delegate;
      this.limitListener = limitListener;
    }

    void await() {
      try {
        latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        limitListener.onIgnore();
        throw new RuntimeException("Interrupted while blocking on a throttled call", e);
      }
    }

    @Override public void onSuccess(V value) {
      try {
        limitListener.onSuccess();
        delegate.onSuccess(value);
      } finally {
        latch.countDown();
      }
    }

    @Override public void onError(Throwable t) {
      try {
        if (t instanceof RejectedExecutionException) {
          limitListener.onDropped();
        } else {
          limitListener.onIgnore();
        }

        delegate.onError(t);
      } finally {
        latch.countDown();
      }
    }

    @Override public String toString() {
      return "Throttled(" + delegate + ")";
    }
  }
}
