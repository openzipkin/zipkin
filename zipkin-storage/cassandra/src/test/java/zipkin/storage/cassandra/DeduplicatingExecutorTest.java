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
package zipkin.storage.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.Reflection;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.Test;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeduplicatingExecutorTest {
  TestDeduplicatingExecutor executor = TestDeduplicatingExecutor.create(Futures::immediateFuture);

  BoundStatement first = mock(BoundStatement.class);
  BoundStatement next = mock(BoundStatement.class);

  @Test
  public void expiresWhenTtlPasses() throws Exception {
    executor.nanoTime = 0;

    ListenableFuture<Void> firstFoo = executor.maybeExecuteAsync(first, "foo");

    // same result for the key foo
    assertThat(executor.maybeExecuteAsync(next, "foo"))
        .isEqualTo(firstFoo);

    executor.nanoTime = TimeUnit.MILLISECONDS.toNanos(500);

    // still, same result for the foo
    assertThat(executor.maybeExecuteAsync(next, "foo"))
        .isEqualTo(firstFoo);

    // add a key for the element that happened after "foo"
    ListenableFuture<Void> firstBar = executor.maybeExecuteAsync(first, "bar");
    assertThat(firstBar)
        .isNotEqualTo(firstFoo);

    // A second after the first call, we should try again
    executor.nanoTime = TimeUnit.SECONDS.toNanos(1);

    // first key refreshes
    assertThat(executor.maybeExecuteAsync(next, "foo"))
        .isNotEqualTo(firstFoo);

    // second key still caching
    assertThat(executor.maybeExecuteAsync(first, "bar"))
        .isEqualTo(firstBar);
  }

  @Test
  public void exceptionArentCached_immediateFuture() throws Exception {
    executor = TestDeduplicatingExecutor.create(s -> {
      if (s == first) return Futures.immediateFailedFuture(new IllegalArgumentException());
      return Futures.immediateFuture(s);
    });
    exceptionsArentCached();
  }

  @Test
  public void exceptionArentCached_deferredFuture() throws Exception {
    ListeningExecutorService exec = listeningDecorator(Executors.newSingleThreadExecutor());
    try {
      executor = TestDeduplicatingExecutor.create(s -> {
        if (s == first) {
          return exec.submit(() -> {
            Thread.sleep(50);
            throw new IllegalArgumentException();
          });
        }
        return Futures.immediateFuture(s);
      });
      exceptionsArentCached();
    } finally {
      exec.shutdownNow();
    }
  }

  @Test
  public void exceptionArentCached_creatingFuture() throws Exception {
    executor = TestDeduplicatingExecutor.create(s -> {
      if (s == first) throw new IllegalArgumentException();
      return Futures.immediateFuture(s);
    });
    exceptionsArentCached();
  }

  private void exceptionsArentCached() throws Exception {
    executor.nanoTime = 0;

    // Intentionally not dereferencing the future. We need to ensure that dropped failed
    // futures still purge!
    ListenableFuture<?> firstFoo = executor.maybeExecuteAsync(first, "foo");

    Thread.sleep(100); // wait a bit for the future to execute and cache to purge the entry

    // doesn't cache exception
    assertThat(executor.maybeExecuteAsync(next, "foo"))
        .isNotEqualTo(firstFoo);

    // sanity check the first future actually failed
    try {
      firstFoo.get();
      failBecauseExceptionWasNotThrown(ExecutionException.class);
    } catch (ExecutionException e) {
      assertThat(e).hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  /**
   * This shows that any number of threads perform a computation only once.
   */
  @Test
  public void multithreaded() throws Exception {
    Session session = mock(Session.class);
    DeduplicatingExecutor executor =
        new DeduplicatingExecutor(session, TimeUnit.SECONDS.toMillis(1L));
    BoundStatement statement = mock(BoundStatement.class);
    when(session.executeAsync(statement))
        .thenAnswer(invocationOnMock -> mock(ResultSetFuture.class));

    int loopCount = 1000;
    CountDownLatch latch = new CountDownLatch(loopCount);
    ExecutorService exec = Executors.newFixedThreadPool(10);

    Collection<ListenableFuture<?>> futures = new ConcurrentLinkedDeque<>();
    for (int i = 0; i < loopCount; i++) {
      exec.execute(() -> {
        futures.add(executor.maybeExecuteAsync(statement, "foo"));
        futures.add(executor.maybeExecuteAsync(statement, "bar"));
        latch.countDown();
      });
    }
    latch.await();

    ImmutableSet<ListenableFuture<?>> distinctFutures = ImmutableSet.copyOf(futures);

    assertThat(distinctFutures).hasSize(2);

    // expire the result
    Thread.sleep(1000L);

    // Sanity check: we don't memoize after we should have expired.
    assertThat(executor.maybeExecuteAsync(statement, "foo"))
        .isNotIn(distinctFutures);
    assertThat(executor.maybeExecuteAsync(statement, "bar"))
        .isNotIn(distinctFutures);
  }

  @Test
  public void expiresWhenTtlPasses_initiallyNegative() throws Exception {
    executor.nanoTime = -TimeUnit.SECONDS.toNanos(1);

    ListenableFuture<Void> firstFuture = executor.maybeExecuteAsync(first, "foo");
    assertThat(executor.maybeExecuteAsync(next, "foo"))
        .isEqualTo(firstFuture);

    // A second after the first call, we should try again
    executor.nanoTime = 0;

    assertThat(executor.maybeExecuteAsync(next, "foo"))
        .isNotEqualTo(firstFuture);
  }

  static class TestDeduplicatingExecutor extends DeduplicatingExecutor {
    static TestDeduplicatingExecutor create(Function<BoundStatement, ListenableFuture<?>> callee) {
      return new TestDeduplicatingExecutor(callee);
    }

    final Function<BoundStatement, ListenableFuture<?>> delegate;
    long nanoTime;

    protected TestDeduplicatingExecutor(Function<BoundStatement, ListenableFuture<?>> delegate) {
      super(fakeSession(delegate), TimeUnit.SECONDS.toMillis(1L));
      this.delegate = delegate;
    }

    @Override long nanoTime() {
      return nanoTime;
    }

    @Override ListenableFuture<?> executeAsync(BoundStatement statement) {
      return delegate.apply(statement);
    }
  }

  static Session fakeSession(final Function<BoundStatement, ListenableFuture<?>> delegate) {
    return Reflection.newProxy(Session.class, (proxy, method, args) -> {
      assert method.getName().equals("executeAsync") && args[0] instanceof BoundStatement;
      return delegate.apply((BoundStatement) args[0]);
    });
  }
}
