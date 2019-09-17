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

import com.linecorp.armeria.common.util.Exceptions;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Limiter.Listener;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Predicate;
import zipkin2.Call;
import zipkin2.Callback;

import static com.linecorp.armeria.common.util.Exceptions.clearTrace;

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
  /**
   * <p>This reduces allocations when concurrency reached by always returning the same instance.
   * This is only thrown in one location, and a stack trace starting from static initialization
   * isn't useful. Hence, we {@link Exceptions#clearTrace clear the trace}.
   */
  static final RejectedExecutionException STORAGE_THROTTLE_MAX_CONCURRENCY =
    clearTrace(new RejectedExecutionException("STORAGE_THROTTLE_MAX_CONCURRENCY reached"));

  static final Callback<Void> NOOP_CALLBACK = new Callback<Void>() {
    @Override public void onSuccess(Void value) {
    }

    @Override public void onError(Throwable t) {
    }
  };

  final Call<Void> delegate;
  final Executor executor;
  final Limiter<Void> limiter;
  final LimiterMetrics limiterMetrics;
  final Predicate<Throwable> isOverCapacity;
  final CountDownLatch latch = new CountDownLatch(1);
  Throwable throwable; // thread visibility guaranteed by the countdown latch

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
    // Enqueue the call invocation on the executor and block until it completes.
    doEnqueue(NOOP_CALLBACK);
    if (!await(latch)) throw new InterruptedIOException();

    // Check if the run resulted in an exception
    Throwable t = this.throwable;
    if (t == null) return null; // success

    // Coerce the throwable to the signature of Call.execute()
    if (t instanceof Error) throw (Error) t;
    if (t instanceof IOException) throw (IOException) t;
    if (t instanceof RuntimeException) throw (RuntimeException) t;
    throw new RuntimeException(t);
  }

  // When handling enqueue, we don't block the calling thread. Any exception goes to the callback.
  @Override protected void doEnqueue(Callback<Void> callback) {
    Listener limiterListener =
      limiter.acquire(null).orElseThrow(() -> STORAGE_THROTTLE_MAX_CONCURRENCY);

    limiterMetrics.requests.increment();
    EnqueueAndAwait enqueueAndAwait = new EnqueueAndAwait(callback, limiterListener);

    try {
      executor.execute(enqueueAndAwait);
    } catch (RuntimeException | Error t) { // possibly rejected, but from the executor, not storage!
      propagateIfFatal(t);
      callback.onError(t);
      // Ignoring in all cases here because storage itself isn't saying we need to throttle. Though
      // we may still be write bound, but a drop in concurrency won't necessarily help.
      limiterListener.onIgnore();
      throw t; // allows blocking calls to see the exception
    }
  }

  @Override public Call<Void> clone() {
    return new ThrottledCall(delegate.clone(), executor, limiter, limiterMetrics, isOverCapacity);
  }

  @Override public String toString() {
    return "Throttled(" + delegate + ")";
  }

  /** When run, this enqueues a call with a given callback, and awaits its completion. */
  final class EnqueueAndAwait implements Runnable, Callback<Void> {
    final Callback<Void> callback;
    final Listener limiterListener;

    EnqueueAndAwait(Callback<Void> callback, Listener limiterListener) {
      this.callback = callback;
      this.limiterListener = limiterListener;
    }

    /**
     * This waits until completion to ensure the number of executing calls doesn't surpass the
     * concurrency limit of the executor.
     *
     * <h3>The {@link Listener} isn't affected during run</h3>
     * There could be an error enqueuing the call or an interruption during shutdown of the
     * executor. We do not affect the {@link Listener} here because it would be redundant to
     * handling already done in callbacks. For example, if shutting down, the storage layer would
     * also invoke {@link #onError(Throwable)}.
     */
    @Override public void run() {
      if (delegate.isCanceled()) return;
      try {
        delegate.enqueue(this);

        // Need to wait here since the callback call will run asynchronously also.
        // This ensures we don't exceed our throttle/queue limits.
        await(latch);
      } catch (Throwable t) { // edge case: error during enqueue!
        propagateIfFatal(t);
        callback.onError(t);
      }
    }

    @Override public void onSuccess(Void value) {
      try {
        // usually we don't add metrics like this,
        // but for now it is helpful to sanity check acquired vs erred.
        limiterMetrics.requestsSucceeded.increment();
        limiterListener.onSuccess(); // NOTE: limiter could block and delay the caller's callback
        callback.onSuccess(value);
      } finally {
        latch.countDown();
      }
    }

    @Override public void onError(Throwable t) {
      try {
        throwable = t; // catch the throwable in case the invocation is blocking (Call.execute())
        if (isOverCapacity.test(t)) {
          limiterMetrics.requestsDropped.increment();
          limiterListener.onDropped();
        } else {
          limiterMetrics.requestsIgnored.increment();
          limiterListener.onIgnore();
        }

        // NOTE: the above limiter could block and delay the caller's callback
        callback.onError(t);
      } finally {
        latch.countDown();
      }
    }

    @Override public String toString() {
      return "EnqueueAndAwait{call=" + delegate + ", callback=" + callback + "}";
    }
  }

  /** Returns true if uninterrupted waiting for the latch */
  static boolean await(CountDownLatch latch) {
    try {
      latch.await();
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
