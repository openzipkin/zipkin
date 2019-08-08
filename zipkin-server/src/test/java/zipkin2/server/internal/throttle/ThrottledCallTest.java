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

import com.linecorp.armeria.common.metric.NoopMeterRegistry;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Limiter.Listener;
import com.netflix.concurrency.limits.limit.SettableLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import org.junit.After;
import org.junit.Test;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.reporter.AwaitableCallback;

import static com.linecorp.armeria.common.util.Exceptions.clearTrace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static zipkin2.server.internal.throttle.ThrottledCall.NOOP_CALLBACK;
import static zipkin2.server.internal.throttle.ThrottledCall.STORAGE_THROTTLE_MAX_CONCURRENCY;
import static zipkin2.server.internal.throttle.ThrottledStorageComponent.STORAGE_THROTTLE_MAX_QUEUE_SIZE;

public class ThrottledCallTest {
  SettableLimit limit = SettableLimit.startingAt(0);
  SimpleLimiter limiter = SimpleLimiter.newBuilder().limit(limit).build();
  LimiterMetrics limiterMetrics = new LimiterMetrics(NoopMeterRegistry.get());
  Predicate<Throwable> isOverCapacity = RejectedExecutionException.class::isInstance;

  int numThreads = 1;
  ExecutorService executor = Executors.newSingleThreadExecutor();

  @After public void shutdownExecutor() {
    executor.shutdown();
  }

  @Test public void niceToString() {
    Call<Void> delegate = mock(Call.class);
    when(delegate.toString()).thenReturn("StoreSpansCall{}");

    assertThat(new ThrottledCall(delegate, executor, limiter, limiterMetrics, isOverCapacity))
      .hasToString("Throttled(StoreSpansCall{})");
  }

  @Test public void execute_isThrottled() throws Exception {
    int queueSize = 1;
    int totalTasks = numThreads + queueSize;
    limit.setLimit(totalTasks);

    Semaphore startLock = new Semaphore(numThreads);
    Semaphore waitLock = new Semaphore(totalTasks);
    Semaphore failLock = new Semaphore(1);
    ThrottledCall throttled = throttle(new LockedCall(startLock, waitLock));

    // Step 1: drain appropriate locks
    startLock.drainPermits();
    waitLock.drainPermits();
    failLock.drainPermits();

    // Step 2: saturate threads and fill queue
    ExecutorService backgroundPool = Executors.newCachedThreadPool();
    for (int i = 0; i < totalTasks; i++) {
      backgroundPool.submit(() -> throttled.clone().execute());
    }

    try {
      // Step 3: make sure the threads actually started
      startLock.acquire(numThreads);

      // Step 4: submit something beyond our limits
      Future<?> future = backgroundPool.submit(() -> {
        try {
          throttled.execute();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        } finally {
          // Step 6: signal that we tripped the limit
          failLock.release();
        }
      });

      // Step 5: wait to make sure our limit actually tripped
      failLock.acquire();

      future.get();

      // Step 7: Expect great things
      failBecauseExceptionWasNotThrown(ExecutionException.class);
    } catch (ExecutionException t) {
      assertThat(t)
        .isInstanceOf(ExecutionException.class) // from future.get
        .hasCauseInstanceOf(RejectedExecutionException.class);
    } finally {
      waitLock.release(totalTasks);
      startLock.release(totalTasks);
      backgroundPool.shutdownNow();
    }
  }

  @Test public void execute_throttlesBack_whenStorageRejects() throws Exception {
    Listener listener = mock(Listener.class);
    FakeCall call = new FakeCall();
    call.overCapacity = true;

    ThrottledCall throttle =
      new ThrottledCall(call, executor, mockLimiter(listener), limiterMetrics, isOverCapacity);

    try {
      throttle.execute();
      assertThat(true).isFalse(); // should raise a RejectedExecutionException
    } catch (RejectedExecutionException e) {
      verify(listener).onDropped();
    }
  }

  @Test public void execute_ignoresLimit_whenPoolFull() throws Exception {
    Listener listener = mock(Listener.class);

    ThrottledCall throttle = new ThrottledCall(new FakeCall(), mockExhaustedPool(),
      mockLimiter(listener), limiterMetrics, isOverCapacity);

    try {
      throttle.execute();
      assertThat(true).isFalse(); // should raise a RejectedExecutionException
    } catch (RejectedExecutionException e) {
      verify(listener).onIgnore();
    }
  }

  @Test public void enqueue_isThrottled() throws Exception {
    int queueSize = 1;
    int totalTasks = numThreads + queueSize;
    limit.setLimit(totalTasks);

    Semaphore startLock = new Semaphore(numThreads);
    Semaphore waitLock = new Semaphore(totalTasks);
    ThrottledCall throttle = throttle(new LockedCall(startLock, waitLock));

    // Step 1: drain appropriate locks
    startLock.drainPermits();
    waitLock.drainPermits();

    // Step 2: saturate threads and fill queue
    Callback<Void> callback = mock(Callback.class);
    for (int i = 0; i < totalTasks; i++) {
      throttle.clone().enqueue(callback);
    }

    // Step 3: make sure the threads actually started
    startLock.acquire(numThreads);

    // Step 4: submit something beyond our limits and make sure it fails
    assertThatThrownBy(() -> throttle.clone().enqueue(callback))
      .isEqualTo(STORAGE_THROTTLE_MAX_CONCURRENCY);
  }

  @Test public void enqueue_throttlesBack_whenStorageRejects() {
    Listener listener = mock(Listener.class);
    FakeCall call = new FakeCall();
    call.overCapacity = true;

    ThrottledCall throttle =
      new ThrottledCall(call, executor, mockLimiter(listener), limiterMetrics, isOverCapacity);

    AwaitableCallback callback = new AwaitableCallback();
    throttle.enqueue(callback);

    assertThatThrownBy(callback::await).isEqualTo(OVER_CAPACITY);

    verify(listener).onDropped();
  }

  @Test public void enqueue_ignoresLimit_whenPoolFull() {
    Listener listener = mock(Listener.class);

    ThrottledCall throttle = new ThrottledCall(new FakeCall(), mockExhaustedPool(),
      mockLimiter(listener), limiterMetrics, isOverCapacity);

    assertThatThrownBy(() -> throttle.enqueue(NOOP_CALLBACK))
      .isEqualTo(STORAGE_THROTTLE_MAX_QUEUE_SIZE);

    verify(listener).onIgnore();
  }

  ThrottledCall throttle(Call<Void> delegate) {
    return new ThrottledCall(delegate, executor, limiter, limiterMetrics, isOverCapacity);
  }

  static final class LockedCall extends Call.Base<Void> {
    final Semaphore startLock, waitLock;

    LockedCall(Semaphore startLock, Semaphore waitLock) {
      this.startLock = startLock;
      this.waitLock = waitLock;
    }

    @Override public Void doExecute() {
      try {
        startLock.release();
        waitLock.acquire();
        return null;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }

    @Override public void doEnqueue(Callback<Void> callback) {
      try {
        callback.onSuccess(doExecute());
      } catch (Throwable t) {
        propagateIfFatal(t);
        callback.onError(t);
      }
    }

    @Override public LockedCall clone() {
      return new LockedCall(startLock, waitLock);
    }
  }

  ExecutorService mockExhaustedPool() {
    ExecutorService mock = mock(ExecutorService.class);
    doThrow(STORAGE_THROTTLE_MAX_QUEUE_SIZE).when(mock).execute(any());
    doThrow(STORAGE_THROTTLE_MAX_QUEUE_SIZE).when(mock).submit(any(Callable.class));
    return mock;
  }

  Limiter<Void> mockLimiter(Listener listener) {
    Limiter<Void> mock = mock(Limiter.class);
    when(mock.acquire(any())).thenReturn(Optional.of(listener));
    return mock;
  }

  static final Exception OVER_CAPACITY = clearTrace(new RejectedExecutionException("overCapacity"));

  static final class FakeCall extends Call.Base<Void> {
    boolean overCapacity = false;

    @Override public Void doExecute() {
      throw new AssertionError("throttling never uses execute");
    }

    @Override public void doEnqueue(Callback<Void> callback) {
      if (overCapacity) {
        callback.onError(OVER_CAPACITY);
      } else {
        callback.onSuccess(null);
      }
    }

    @Override public FakeCall clone() {
      return new FakeCall();
    }
  }
}
