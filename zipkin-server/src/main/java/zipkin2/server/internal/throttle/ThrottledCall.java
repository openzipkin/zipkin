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
package zipkin2.server.internal.throttle;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Limiter.Listener;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
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
final class ThrottledCall extends Call.Base<Void> {
  final Call<Void> delegate;
  final Executor executor;
  final Limiter<Void> limiter;
  final LimiterMetrics limiterMetrics;
  final Predicate<Throwable> isOverCapacity;

  ThrottledCall(Call<Void> delegate, Executor executor, Limiter<Void> limiter,
    LimiterMetrics limiterMetrics, Predicate<Throwable> isOverCapacity) {
    this.delegate = delegate;
    this.executor = executor;
    this.limiter = limiter;
    this.limiterMetrics = limiterMetrics;
    this.isOverCapacity = isOverCapacity;
  }

  /**
   * To simplify code, this doesn't actually invoke the underlying {@link #execute()} method. This
   * is ok because in almost all cases, doing so would imply invoking {@link #enqueue(Callback)}
   * anyway.
   */
  @Override protected Void doExecute() throws IOException {
    AwaitableCallback awaitableCallback = new AwaitableCallback();
    doEnqueue(awaitableCallback);
    if (!await(awaitableCallback.countDown)) throw new InterruptedIOException();

    Throwable t = awaitableCallback.throwable.get();
    if (t != null) {
      if (t instanceof Error) throw (Error) t;
      if (t instanceof IOException) throw (IOException) t;
      if (t instanceof RuntimeException) throw (RuntimeException) t;
      throw new RuntimeException(t);
    }
    return null; // Void
  }

  @Override protected void doEnqueue(Callback<Void> callback) {
    Listener limiterListener = limiter.acquire(null)
      .orElseThrow(RejectedExecutionException::new); // TODO: make an exception message
    limiterMetrics.requests.increment();
    limiterListener = limiterMetrics.wrap(limiterListener);

    LimiterReleasingCallback releasingCallback =
      new LimiterReleasingCallback(callback, isOverCapacity, limiterListener);

    try {
      executor.execute(new EnqueueAndAwait(this, releasingCallback));
    } catch (RuntimeException | Error t) { // possibly rejected, but from the executor, not storage!
      propagateIfFatal(t);
      callback.onError(t);
      // Ignoring in all cases here because storage itself isn't saying we need to throttle. Though
      // we may still be write bound, but a drop in concurrency won't necessarily help.
      limiterListener.onIgnore();
      throw t;
    }
  }

  @Override public Call<Void> clone() {
    return new ThrottledCall(delegate.clone(), executor, limiter, limiterMetrics, isOverCapacity);
  }

  @Override public String toString() {
    return "Throttled(" + delegate + ")";
  }

  static final class AwaitableCallback implements Callback<Void> {
    final CountDownLatch countDown = new CountDownLatch(1);
    final AtomicReference<Throwable> throwable = new AtomicReference<>();

    @Override public void onSuccess(Void ignored) {
      countDown.countDown();
    }

    @Override public void onError(Throwable t) {
      throwable.set(t);
      countDown.countDown();
    }
  }

  static final class EnqueueAndAwait implements Runnable {
    final Call<Void> delegate;
    final Predicate<Throwable> isOverCapacity;
    final LimiterReleasingCallback callback;

    EnqueueAndAwait(ThrottledCall throttledCall, LimiterReleasingCallback callback) {
      this.delegate = throttledCall.delegate;
      this.isOverCapacity = throttledCall.isOverCapacity;
      this.callback = callback;
    }

    /**
     * This awaits callback completion in order to slow down (throttle) calls.
     *
     * <h3>This component does not affect the {@link Listener} directly</h3>
     * There could be an error enqueuing the call or an interruption during shutdown of the
     * executor. We do not affect the {@link Listener} here because it would be redundant to
     * handling already done in {@link LimiterReleasingCallback}. For example, if shutting down, the
     * storage layer would also invoke {@link LimiterReleasingCallback#onError(Throwable)}.
     */
    @Override public void run() {
      if (delegate.isCanceled()) return;
      try {
        delegate.enqueue(callback);

        // Need to wait here since the callback call will run asynchronously also.
        // This ensures we don't exceed our throttle/queue limits.
        await(callback.latch);
      } catch (Throwable t) { // edge case: error during enqueue!
        propagateIfFatal(t);
        callback.onError(t);
      }
    }

    @Override public String toString() {
      return "EnqueueAndAwait{call=" + delegate + ", callback=" + callback.delegate + "}";
    }
  }

  /**
   * Returns true if not interrupted.
   *
   * @see zipkin2.reporter.AwaitableCallback for tested code with the same await loop behavior.
   */
  static boolean await(CountDownLatch latch) {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          latch.await();
          return true;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  static final class LimiterReleasingCallback implements Callback<Void> {
    final Callback<Void> delegate;
    final Predicate<Throwable> isOverCapacity;
    final Listener limiterListener;
    final CountDownLatch latch = new CountDownLatch(1);

    LimiterReleasingCallback(Callback<Void> delegate, Predicate<Throwable> isOverCapacity,
      Listener limiterListener) {
      this.delegate = delegate;
      this.isOverCapacity = isOverCapacity;
      this.limiterListener = limiterListener;
    }

    @Override public void onSuccess(Void value) {
      try {
        limiterListener.onSuccess(); // NOTE: limiter could block and delay the caller's callback
        delegate.onSuccess(value);
      } finally {
        latch.countDown();
      }
    }

    @Override public void onError(Throwable t) {
      try {
        if (isOverCapacity.test(t)) {
          limiterListener.onDropped();
        } else {
          limiterListener.onIgnore();
        }

        // NOTE: the above limiter could block and delay the caller's callback
        delegate.onError(t);
      } finally {
        latch.countDown();
      }
    }

    @Override public String toString() {
      return "LimiterReleasingCallback(" + delegate + ")";
    }
  }
}
