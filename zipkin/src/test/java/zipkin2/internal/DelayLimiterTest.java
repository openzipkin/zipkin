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
package zipkin2.internal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest(DelayLimiter.class)
public class DelayLimiterTest {
  static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

  @Test public void mutesDuringDelayPeriod() {
    mockStatic(System.class);
    DelayLimiter<Long> delayLimiter =
      DelayLimiter.newBuilder().expireAfter(3, TimeUnit.SECONDS).build();

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND);
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 2);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 2);
    assertThat(delayLimiter.shouldInvoke(0L)).isFalse();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
  }

  @Test public void contextsAreIndependent() {
    mockStatic(System.class);
    DelayLimiter<Long> delayLimiter =
      DelayLimiter.newBuilder().expireAfter(3, TimeUnit.SECONDS).build();

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND);
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 2);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 2);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 2);
    assertThat(delayLimiter.shouldInvoke(0L)).isFalse();
    assertThat(delayLimiter.shouldInvoke(1L)).isTrue();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
    assertThat(delayLimiter.shouldInvoke(1L)).isFalse();
  }

  @Test public void worksOnRollover() {
    mockStatic(System.class);
    DelayLimiter<Long> delayLimiter =
      DelayLimiter.newBuilder().expireAfter(3, TimeUnit.SECONDS).build();

    when(System.nanoTime()).thenReturn(-NANOS_PER_SECOND);
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
    when(System.nanoTime()).thenReturn(0L);
    when(System.nanoTime()).thenReturn(0L);
    assertThat(delayLimiter.shouldInvoke(0L)).isFalse();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 2);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 2);
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
  }

  @Test public void worksOnSameNanos() {
    mockStatic(System.class);
    DelayLimiter<Long> delayLimiter =
      DelayLimiter.newBuilder().expireAfter(3, TimeUnit.SECONDS).build();

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND);
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND * 4);
    assertThat(delayLimiter.shouldInvoke(0L)).isFalse();
  }

  @Test(timeout = 15000L)
  public void maximumSize() {
    DelayLimiter<Long> delayLimiter = DelayLimiter.newBuilder()
      .expireAfter(15, TimeUnit.SECONDS)
      .maximumSize(1000)
      .build();

    for (long i = 0; i < 10_000L; i++) {
      assertThat(delayLimiter.shouldInvoke(i)).isTrue();
    }
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue(); // evicted
    assertThat(delayLimiter.shouldInvoke(9_999L)).isFalse(); // not evicted

    // verify internal state
    assertThat(delayLimiter.cache)
      .hasSameSizeAs(delayLimiter.suppressions)
      .hasSize(1000);
  }

  @Test(timeout = 15000L)
  public void maximumSize_parallel() throws InterruptedException {
    DelayLimiter<Long> delayLimiter = DelayLimiter.newBuilder()
      .expireAfter(15, TimeUnit.SECONDS)
      .maximumSize(1000)
      .build();

    AtomicInteger trueCount = new AtomicInteger();
    ExecutorService exec = Executors.newFixedThreadPool(4);

    int count = 1500;
    CountDownLatch latch = new CountDownLatch(count);
    LongStream.range(0, count).forEach(i -> exec.execute(() -> {
      if (delayLimiter.shouldInvoke(i)) trueCount.incrementAndGet();
      latch.countDown();
    }));

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    exec.shutdown();
    assertThat(exec.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    assertThat(trueCount.get()).isEqualTo(count);

    // verify internal state
    assertThat(delayLimiter.cache)
      .hasSameSizeAs(delayLimiter.suppressions)
      .hasSize(1000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void expireAfter_cantBeNegative() {
    DelayLimiter.newBuilder().expireAfter(-1, TimeUnit.SECONDS);
  }

  @Test(expected = IllegalArgumentException.class)
  public void expireAfter_cantBeZero() {
    DelayLimiter.newBuilder().expireAfter(0, TimeUnit.SECONDS);
  }

  @Test(expected = IllegalArgumentException.class)
  public void maximumSize_cantBeNegative() {
    DelayLimiter.newBuilder().maximumSize(-1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void maximumSize_cantBeZero() {
    DelayLimiter.newBuilder().maximumSize(0);
  }
}
