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
import com.netflix.concurrency.limits.limit.SettableLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import zipkin2.Call;
import zipkin2.Callback;

public class ThrottledCallTest {
  SettableLimit limit;
  Limiter<Void> limiter;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    this.limit = SettableLimit.startingAt(0);
    this.limiter = SimpleLimiter.newBuilder().limit(limit).build();
  }

  @Test
  public void callCreation_isDeferred() throws IOException {
    boolean[] created = new boolean[] {false};
    Supplier<Call<Void>> delegate = () -> {
      created[0] = true;
      return Call.create(null);
    };

    ThrottledCall<Void> throttle = createThrottle(delegate);

    assertFalse(created[0]);
    throttle.execute();
    assertTrue(created[0]);
  }

  @Test
  public void execute_isThrottled() throws Throwable {
    int numThreads = 1;
    int queueSize = 1;
    int totalTasks = numThreads + queueSize;

    Semaphore startLock = new Semaphore(numThreads);
    Semaphore waitLock = new Semaphore(totalTasks);
    Semaphore failLock = new Semaphore(1);
    Supplier<Call<Void>> delegate = () -> new LockedCall(startLock, waitLock);
    ThrottledCall<Void> throttle = createThrottle(numThreads, queueSize, delegate);

    // Step 1: drain appropriate locks
    startLock.drainPermits();
    waitLock.drainPermits();
    failLock.drainPermits();

    // Step 2: saturate threads and fill queue
    ExecutorService backgroundPool = Executors.newCachedThreadPool();
    for (int i = 0; i < totalTasks; i++) {
      backgroundPool.submit(throttle::execute);
    }

    try {
      // Step 3: make sure the threads actually started
      startLock.acquire(numThreads);

      // Step 4: submit something beyond our limits
      Future<Object> future = backgroundPool.submit(() -> {
        try {
          return throttle.execute();
        } catch (IOException e) {
          throw new RuntimeException(e);
        } finally {
          // Step 6: signal that we tripped the limit
          failLock.release();
        }
      });

      // Step 5: wait to make sure our limit actually tripped
      failLock.acquire();

      // Step 7: Expect great things
      expectedException.expect(RejectedExecutionException.class);
      future.get();
    } catch (ExecutionException e) {
      throw e.getCause();
    } finally {
      waitLock.release(totalTasks);
      startLock.release(totalTasks);
      backgroundPool.shutdownNow();
    }
  }

  @Test
  public void execute_trottlesBack_whenStorageRejects() throws IOException {
    Listener listener = mock(Listener.class);
    FakeCall call = new FakeCall();
    call.setOverCapacity(true);

    ThrottledCall<Void> throttle = new ThrottledCall<>(createPool(1, 1), mockLimiter(listener), () -> call);
    try {
      throttle.execute();
      fail("No Exception thrown");
    } catch (RejectedExecutionException e) {
      verify(listener).onDropped();
    }
  }

  @Test
  public void execute_ignoresLimit_whenPoolFull() throws IOException {
    Listener listener = mock(Listener.class);

    ThrottledCall<Void> throttle = new ThrottledCall<>(mockExhuastedPool(), mockLimiter(listener), FakeCall::new);
    try {
      throttle.execute();
      fail("No Exception thrown");
    } catch (RejectedExecutionException e) {
      verify(listener).onIgnore();
    }
  }

  @Test
  public void enqueue_isThrottled() throws Throwable {
    int numThreads = 1;
    int queueSize = 1;
    int totalTasks = numThreads + queueSize;

    Semaphore startLock = new Semaphore(numThreads);
    Semaphore waitLock = new Semaphore(totalTasks);
    Supplier<Call<Void>> delegate = () -> new LockedCall(startLock, waitLock);
    ThrottledCall<Void> throttle = createThrottle(numThreads, queueSize, delegate);

    // Step 1: drain appropriate locks
    startLock.drainPermits();
    waitLock.drainPermits();

    // Step 2: saturate threads and fill queue
    Callback<Void> callback = new NoopCallback();
    for (int i = 0; i < totalTasks; i++) {
      throttle.enqueue(callback);
    }

    // Step 3: make sure the threads actually started
    startLock.acquire(numThreads);

    try {
      // Step 4: submit something beyond our limits and make sure it fails
      expectedException.expect(RejectedExecutionException.class);
      throttle.enqueue(callback);
    } catch (Exception e) {
      throw e;
    } finally {
      waitLock.release(totalTasks);
      startLock.release(totalTasks);
    }
  }

  @Test
  public void enqueue_trottlesBack_whenStorageRejects() throws IOException, InterruptedException {
    Listener listener = mock(Listener.class);
    FakeCall call = new FakeCall();
    call.setOverCapacity(true);

    ThrottledCall<Void> throttle = new ThrottledCall<>(createPool(1, 1), mockLimiter(listener), () -> call);
    CountDownLatch latch = new CountDownLatch(1);
    throttle.enqueue(new Callback<Void>() {
      @Override
      public void onSuccess(Void value) {
        latch.countDown();
      }

      @Override
      public void onError(Throwable t) {
        latch.countDown();
      }
    });

    latch.await(1, TimeUnit.MINUTES);
    verify(listener).onDropped();
  }

  @Test
  public void enqueue_ignoresLimit_whenPoolFull() throws IOException {
    Listener listener = mock(Listener.class);

    ThrottledCall<Void> throttle = new ThrottledCall<>(mockExhuastedPool(), mockLimiter(listener), FakeCall::new);
    try {
      throttle.enqueue(null);
      fail("No Exception thrown");
    } catch (RejectedExecutionException e) {
      verify(listener).onIgnore();
    }
  }

  ThrottledCall<Void> createThrottle(Supplier<Call<Void>> delegate) {
    return createThrottle(1, 1, delegate);
  }

  ThrottledCall<Void> createThrottle(int poolSize, int queueSize, Supplier<Call<Void>> delegate) {
    limit.setLimit(limit.getLimit() + 1);
    return new ThrottledCall<>(createPool(poolSize, queueSize), limiter, delegate);
  }

  static ExecutorService createPool(int poolSize, int queueSize) {
    return new ThreadPoolExecutor(poolSize, poolSize, 0, TimeUnit.DAYS, new LinkedBlockingQueue<>(queueSize));
  }

  static ExecutorService mockExhuastedPool() {
    ExecutorService mock = mock(ExecutorService.class);
    doThrow(RejectedExecutionException.class).when(mock).execute(any());
    doThrow(RejectedExecutionException.class).when(mock).submit(any(Callable.class));
    return mock;
  }

  static Limiter<Void> mockLimiter(Listener listener) {
    Limiter<Void> mock = mock(Limiter.class);
    when(mock.acquire(any())).thenReturn(Optional.of(listener));
    return mock;
  }

  static final class LockedCall extends Call<Void> {
    final Semaphore startLock;
    final Semaphore waitLock;

    public LockedCall(Semaphore startLock, Semaphore waitLock) {
      this.startLock = startLock;
      this.waitLock = waitLock;
    }

    @Override
    public Void execute() throws IOException {
      try {
        startLock.release();
        waitLock.acquire();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      return null;
    }

    @Override
    public void enqueue(Callback<Void> callback) {
      try {
        startLock.release();
        waitLock.acquire();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void cancel() {
      throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isCanceled() {
      return false;
    }

    @Override
    public Call<Void> clone() {
      throw new UnsupportedOperationException("Not supported yet.");
    }
  }

  static final class NoopCallback implements Callback<Void> {
    @Override
    public void onSuccess(Void value) {
    }

    @Override
    public void onError(Throwable t) {
    }
  }
}
