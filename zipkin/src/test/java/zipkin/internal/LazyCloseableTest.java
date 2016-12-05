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

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class LazyCloseableTest {
  AtomicInteger callCount = new AtomicInteger();
  TestLazyCloseable<Closeable> alwaysThrow = TestLazyCloseable.create(() -> {
    callCount.incrementAndGet();
    throw new RuntimeException();
  });

  TestLazyCloseable<Closeable> throwOnce = TestLazyCloseable.create(() -> {
    if (callCount.incrementAndGet() == 1) {
      throw new RuntimeException();
    }
    return (Closeable) () -> {
    };
  });

  @Test
  public void expiresExceptionWhenDurationPasses() throws InterruptedException {
    throwOnce.nanoTime = 0;

    expectExceptionOnGet(throwOnce);
    assertThat(callCount.get()).isEqualTo(1);

    // A second after the first call, we should try again
    throwOnce.nanoTime = TimeUnit.SECONDS.toNanos(1);

    assertThat(throwOnce.get()).isNotNull();
    assertThat(callCount.get()).isEqualTo(2);

    // sanity check that we cache from now on
    assertThat(throwOnce.get()).isNotNull();
    assertThat(callCount.get()).isEqualTo(2);
  }

  /**
   * This shows that any number of threads performing a failed computation only fail once.
   */
  @Test(timeout = 2000L) // 1000 for the gets + expiration (which is 1 second)
  public void exception_memoizes() throws InterruptedException {
    // tests an unmodified lazy closeable, which uses system nanos to manage expiration
    LazyCloseable<Closeable> alwaysThrow = new LazyCloseable<Closeable>() {
      @Override protected Closeable compute() {
        throw new RuntimeException(String.valueOf(callCount.incrementAndGet()));
      }
    };

    int getCount = 1000;
    CountDownLatch latch = new CountDownLatch(getCount);
    Executor exec = Executors.newFixedThreadPool(10);
    for (int i = 0; i < getCount; i++) {
      exec.execute(() -> {
        expectExceptionOnGet(alwaysThrow);
        latch.countDown();
      });
    }
    latch.await();

    assertThat(callCount.get()).isEqualTo(1);

    // expire the exception
    Thread.sleep(1000L);

    // Sanity check: we don't memoize after we should have expired.
    expectExceptionOnGet(alwaysThrow);
    assertThat(callCount.get()).isEqualTo(2);
  }

  @Test
  public void expiresExceptionWhenDurationPasses_initiallyNegative() throws InterruptedException {
    alwaysThrow.nanoTime = -TimeUnit.SECONDS.toNanos(1);

    expectExceptionOnGet(alwaysThrow);
    assertThat(callCount.get()).isEqualTo(1);

    // A second after the first call, we should try again
    alwaysThrow.nanoTime = 0;

    expectExceptionOnGet(alwaysThrow);
    assertThat(callCount.get()).isEqualTo(2);
  }

  void expectExceptionOnGet(Lazy<?> alwaysThrow) {
    try {
      alwaysThrow.get();
      failBecauseExceptionWasNotThrown(RuntimeException.class);
    } catch (RuntimeException e) {
    }
  }

  static class TestLazyCloseable<T> extends LazyCloseable<T> {
    static <T> TestLazyCloseable<T> create(Supplier<T> delegate) {
      return new TestLazyCloseable<>(delegate);
    }

    final Supplier<T> delegate;
    long nanoTime;

    protected TestLazyCloseable(Supplier<T> delegate) {
      this.delegate = delegate;
    }

    @Override long nanoTime() {
      return nanoTime;
    }

    @Override protected T compute() {
      return delegate.get();
    }
  }
}
