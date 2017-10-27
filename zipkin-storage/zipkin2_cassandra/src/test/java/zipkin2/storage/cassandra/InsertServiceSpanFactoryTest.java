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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.base.Supplier;
import com.google.common.reflect.Reflection;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.mockito.Answers;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.storage.cassandra.InsertServiceSpan.Input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class InsertServiceSpanFactoryTest {
  TestInsertServiceSpanFactory executor =
    TestInsertServiceSpanFactory.create(InsertServiceSpanFactoryTest::mockedFuture);

  Input first = executor.newInput("service1", "span");
  Input next = executor.newInput("service2", "span");

  @Test
  public void expiresWhenTtlPasses() throws Exception {
    executor.nanoTime = 0;

    // first call happens for real
    Call<ResultSet> firstCall = executor.create(first);
    assertThat(firstCall)
      .isInstanceOf(InsertServiceSpan.class);

    // constant result returned since the first is in flight
    assertThat(executor.create(first))
      .isEqualTo(Call.create(null));

    executor.nanoTime = TimeUnit.MILLISECONDS.toNanos(500);

    // still, same result
    assertThat(executor.create(first))
      .isEqualTo(Call.create(null));

    // add a key for the element that happened after
    assertThat(executor.create(next))
      .isInstanceOf(InsertServiceSpan.class)
      .isNotEqualTo(firstCall);

    // A second after the first call, we should try again
    executor.nanoTime = TimeUnit.SECONDS.toNanos(1);

    // first key refreshes
    assertThat(executor.create(first))
      .isInstanceOf(InsertServiceSpan.class);

    // second key still caching
    assertThat(executor.create(next))
      .isEqualTo(Call.create(null));
  }

  @Test
  public void exceptionArentCached_failedFuture() throws Exception {
    AtomicBoolean first = new AtomicBoolean(true);
    executor = TestInsertServiceSpanFactory.create(() -> {
      ResultSetFuture result = mockedFuture();
      if (first.getAndSet(false)) {
        when(result.getUninterruptibly()).thenThrow(new IllegalArgumentException());
      }
      return result;
    });
    exceptionsArentCached();
  }

  private void exceptionsArentCached() throws Exception {
    executor.nanoTime = 0;
    final CountDownLatch countDown = new CountDownLatch(1);
    final AtomicReference<Object> result = new AtomicReference<>();

    // Intentionally not dereferencing the future. We need to ensure that dropped failed
    // futures still purge!
    executor.create(first).enqueue(new Callback<ResultSet>() {
      @Override public void onSuccess(ResultSet value) {
        result.set(value);
        countDown.countDown();
      }

      @Override public void onError(Throwable t) {
        result.set(t);
        countDown.countDown();
      }
    });

    Thread.sleep(100); // wait a bit for the future to execute and cache to purge the entry

    // doesn't cache exception
    assertThat(executor.create(first))
      .isInstanceOf(InsertServiceSpan.class);

    // sanity check the first future actually failed
    assertThat(result.get())
      .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * This shows that any number of threads perform a computation only once.
   */
  @Test
  public void multithreaded() throws Exception {
    Session session = mock(Session.class);
    InsertServiceSpan.Factory executor = new InsertServiceSpan.Factory(session, 1000L);
    when(session.executeAsync(any(Statement.class)))
      .thenAnswer(invocationOnMock -> mock(ResultSetFuture.class));

    int loopCount = 1000;
    CountDownLatch latch = new CountDownLatch(loopCount);
    ExecutorService exec = Executors.newFixedThreadPool(10);

    Collection<Call<ResultSet>> futures = new ConcurrentLinkedDeque<>();
    for (int i = 0; i < loopCount; i++) {
      exec.execute(() -> {
        try {
          futures.add(executor.create(first));
          futures.add(executor.create(next));
        } finally {
          latch.countDown();
        }
      });
    }
    latch.await();

    assertThat(new LinkedHashSet<>(futures)).hasSize(2 + 1)
      .contains(Call.create(null) /* default value when requests are in-flight */);

    // expire the result
    Thread.sleep(1000L);

    // Sanity check: we don't memoize after we should have expired.
    assertThat(executor.create(first))
      .isInstanceOf(InsertServiceSpan.class);
    assertThat(executor.create(next))
      .isInstanceOf(InsertServiceSpan.class);
  }

  @Test
  public void expiresWhenTtlPasses_initiallyNegative() throws Exception {
    executor.nanoTime = -TimeUnit.SECONDS.toNanos(1);

    assertThat(executor.create(first))
      .isInstanceOf(InsertServiceSpan.class);
    assertThat(executor.create(first))
      .isEqualTo(Call.create(null));

    // A second after the first call, we should try again
    executor.nanoTime = 0;

    assertThat(executor.create(first))
      .isInstanceOf(InsertServiceSpan.class);
  }

  static class TestInsertServiceSpanFactory extends InsertServiceSpan.Factory {
    static TestInsertServiceSpanFactory create(Supplier<ListenableFuture<?>> delegate) {
      return new TestInsertServiceSpanFactory(delegate);
    }

    long nanoTime;

    TestInsertServiceSpanFactory(Supplier<ListenableFuture<?>> delegate) {
      super(fakeSession(delegate), TimeUnit.SECONDS.toMillis(1L));
    }

    @Override long nanoTime() {
      return nanoTime;
    }
  }

  static Session fakeSession(final Supplier<ListenableFuture<?>> delegate) {
    return Reflection.newProxy(Session.class, (proxy, method, args) -> {
      if (method.getName().equals("prepare")) {
        PreparedStatement prepared = mock(PreparedStatement.class);
        BoundStatement bound = mock(BoundStatement.class, withSettings().defaultAnswer(
          Answers.RETURNS_MOCKS.get()));
        when(prepared.bind()).thenReturn(bound);
        return prepared;
      }
      assertThat(method.getName()).isEqualTo("executeAsync");
      return delegate.get();
    });
  }

  static ResultSetFuture mockedFuture() {
    ResultSetFuture result = mock(ResultSetFuture.class);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArguments()[0]).run();
      return null;
    }).when(result).addListener(any(Runnable.class), any(Executor.class));
    return result;
  }
}
