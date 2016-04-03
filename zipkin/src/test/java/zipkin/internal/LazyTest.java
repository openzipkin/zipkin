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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyTest {

  @Test(timeout = 1000L)
  public void get_memoizes() throws InterruptedException {
    int getCount = 1000;

    AtomicInteger value = new AtomicInteger();

    Lazy<Integer> lazyInt = new Lazy<Integer>() {
      final AtomicInteger val = new AtomicInteger();

      @Override protected Integer compute() {
        return val.incrementAndGet();
      }
    };

    CountDownLatch latch = new CountDownLatch(getCount);
    Executor exec = Executors.newFixedThreadPool(10);
    for (int i = 0; i < getCount; i++) {
      exec.execute(() -> {
        // if lazy computes multiple times, the result of lazyInt.get() > 1
        value.getAndAdd(lazyInt.get());
        latch.countDown();
      });
    }
    latch.await();

    assertThat(value.get()).isEqualTo(getCount);
  }
}
