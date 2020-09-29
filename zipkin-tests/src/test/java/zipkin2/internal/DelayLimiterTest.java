/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import zipkin2.internal.DelayLimiter.SuppressionFactory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DelayLimiterTest {
  static final long NANOS_PER_SECOND = SECONDS.toNanos(1L);
  long nanoTime;
  SuppressionFactory suppressionFactory = new SuppressionFactory(NANOS_PER_SECOND) {
    @Override long nanoTime() {
      return nanoTime;
    }
  };
  DelayLimiter<Long> delayLimiter = new DelayLimiter<>(suppressionFactory, 1000);

  @Test void mutesDuringDelayPeriod() {
    nanoTime = NANOS_PER_SECOND;
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();

    nanoTime += NANOS_PER_SECOND / 2L;
    assertThat(delayLimiter.shouldInvoke(0L)).isFalse();

    nanoTime += NANOS_PER_SECOND / 2L;
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
  }

  @Test void contextsAreIndependent() {
    nanoTime = NANOS_PER_SECOND;
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();

    nanoTime += NANOS_PER_SECOND / 2L;
    assertThat(delayLimiter.shouldInvoke(0L)).isFalse();
    assertThat(delayLimiter.shouldInvoke(1L)).isTrue();

    nanoTime += NANOS_PER_SECOND / 2L;
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
    assertThat(delayLimiter.shouldInvoke(1L)).isFalse();
  }

  @Test void worksOnRollover() {
    nanoTime = -NANOS_PER_SECOND / 2L;
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();

    nanoTime = 0L;
    assertThat(delayLimiter.shouldInvoke(0L)).isFalse();

    nanoTime = NANOS_PER_SECOND / 2L;
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
  }

  @Test void worksOnSameNanos() {
    nanoTime = NANOS_PER_SECOND;
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();

    nanoTime = NANOS_PER_SECOND * 2L;
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue();
    assertThat(delayLimiter.shouldInvoke(0L)).isFalse();
  }

  @Test @Timeout(1000L) void cardinality() {
    long count = delayLimiter.cardinality * 10L;
    for (long i = 0L; i < count; i++, nanoTime++) {
      assertThat(delayLimiter.shouldInvoke(i)).isTrue();
    }
    assertThat(delayLimiter.shouldInvoke(0L)).isTrue(); // eldest evicted
    assertThat(delayLimiter.shouldInvoke(count - 1L)).isFalse(); // youngest not evicted

    // verify internal state
    assertThat(delayLimiter.cache)
      .hasSameSizeAs(delayLimiter.suppressions)
      .hasSize(delayLimiter.cardinality);
  }

  @Test @Timeout(2000L) void cardinality_parallel() throws InterruptedException {
    AtomicLong trueCount = new AtomicLong();
    ExecutorService exec = Executors.newFixedThreadPool(4);

    long count = delayLimiter.cardinality * 10L;
    LongStream.range(0L, count).forEach(i -> exec.execute(() -> {
      if (delayLimiter.shouldInvoke(i)) trueCount.incrementAndGet();
    }));

    exec.shutdown();
    assertThat(exec.awaitTermination(1L, SECONDS)).isTrue();

    assertThat(trueCount).hasValue(count);

    // verify internal state
    assertThat(delayLimiter.cache)
      .hasSameSizeAs(delayLimiter.suppressions)
      .hasSize(delayLimiter.cardinality);
  }

  @Test void ttl_cantBeNegative() {
    DelayLimiter.Builder builder = DelayLimiter.newBuilder().ttl(-1, SECONDS);

    assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void ttl_cantBeZero() {
    DelayLimiter.Builder builder = DelayLimiter.newBuilder().ttl(0, SECONDS);

    assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void cardinality_cantBeNegative() {
    DelayLimiter.Builder builder = DelayLimiter.newBuilder().ttl(1L, SECONDS).cardinality(-1);

    assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void cardinality_cantBeZero() {
    DelayLimiter.Builder builder = DelayLimiter.newBuilder().ttl(1L, SECONDS).cardinality(0);

    assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
  }
}
