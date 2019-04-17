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
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.storage.InMemoryStorage;

/**
 * {@link Call} implementation that is backed by an {@link ExecutorService}. The ExecutorService serves two
 * purposes:
 * <ol>
 * <li>Limits the number of requests that can run in parallel.</li>
 * <li>Depending on configuration, can queue up requests to make sure we don't aggressively drop requests that would
 *     otherwise succeed if given a moment. Bounded queues are safest for this as unbounded ones can lead to heap
 *     exhaustion and {@link OutOfMemoryError OOM errors}.</li>
 * </ol>
 *
 * @see ThrottledStorageComponent
 */
final class ThrottledCall<V> extends Call<V> {
  final ExecutorService executor;
  final Limiter<Void> limiter;
  final Listener limitListener;
  /**
   * Delegate call needs to be supplied later to avoid having it take action when it is created (like
   * {@link InMemoryStorage} and thus avoid being throttled.
   */
  final Supplier<Call<V>> delegate;
  Call<V> call;
  volatile boolean canceled;

  public ThrottledCall(ExecutorService executor, Limiter<Void> limiter, Supplier<Call<V>> delegate) {
    this.executor = executor;
    this.limiter = limiter;
    this.limitListener = limiter.acquire(null).orElseThrow(RejectedExecutionException::new);
    this.delegate = delegate;
  }

  private ThrottledCall(ThrottledCall other) {
    this(other.executor, other.limiter, other.call == null ? other.delegate : () -> other.call.clone());
  }

  @Override
  public V execute() throws IOException {
    try {
      call = delegate.get();

      // Make sure we throttle
      Future<V> future = executor.submit(() -> {
        String oldName = setCurrentThreadName(call.toString());
        try {
          return call.execute();
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
    } catch (Exception e) {
      // Ignoring in all cases here because storage itself isn't saying we need to throttle.  Though, we may still be
      // write bound, but a drop in concurrency won't necessarily help.
      limitListener.onIgnore();
      throw e;
    }
  }

  @Override
  public void enqueue(Callback<V> callback) {
    try {
      executor.execute(new QueuedCall(callback));
    } catch (Exception e) {
      // Ignoring in all cases here because storage itself isn't saying we need to throttle.  Though, we may still be
      // write bound, but a drop in concurrency won't necessarily help.
      limitListener.onIgnore();
      throw e;
    }
  }

  @Override
  public void cancel() {
    canceled = true;
    if (call != null) {
      call.cancel();
    }
  }

  @Override
  public boolean isCanceled() {
    return canceled || (call != null && call.isCanceled());
  }

  @Override
  public Call<V> clone() {
    return new ThrottledCall<>(this);
  }

  /**
   * @param name New name for the current Thread
   * @return Previous name of the current Thread
   */
  static String setCurrentThreadName(String name) {
    Thread thread = Thread.currentThread();
    String originalName = thread.getName();
    try {
      thread.setName(name);
      return originalName;
    } catch (SecurityException e) {
      return originalName;
    }
  }

  final class QueuedCall implements Runnable {
    final Callback<V> callback;

    public QueuedCall(Callback<V> callback) {
      this.callback = callback;
    }

    @Override
    public void run() {
      try {
        if (canceled) {
          return;
        }

        call = ThrottledCall.this.delegate.get();

        String oldName = setCurrentThreadName(call.toString());
        try {
          enqueueAndWait();
        } finally {
          setCurrentThreadName(oldName);
        }
      } catch (Exception e) {
        limitListener.onIgnore();
        callback.onError(e);
      }
    }

    void enqueueAndWait() {
      ThrottledCallback<V> throttleCallback = new ThrottledCallback<>(callback, limitListener);
      call.enqueue(throttleCallback);

      // Need to wait here since the callback call will run asynchronously also.
      // This ensures we don't exceed our throttle/queue limits.
      throttleCallback.await();
    }
  }

  static final class ThrottledCallback<V> implements Callback<V> {
    final Callback<V> delegate;
    final Listener limitListener;
    final CountDownLatch latch;

    public ThrottledCallback(Callback<V> delegate, Listener limitListener) {
      this.delegate = delegate;
      this.limitListener = limitListener;
      this.latch = new CountDownLatch(1);
    }

    public void await() {
      try {
        latch.await();
      } catch (InterruptedException e) {
        limitListener.onIgnore();
        throw new RuntimeException("Interrupted while blocking on a throttled call", e);
      }
    }

    @Override
    public void onSuccess(V value) {
      try {
        limitListener.onSuccess();
        delegate.onSuccess(value);
      } finally {
        latch.countDown();
      }
    }

    @Override
    public void onError(Throwable t) {
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
  }
}
