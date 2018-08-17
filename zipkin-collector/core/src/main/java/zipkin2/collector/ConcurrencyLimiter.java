/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.collector;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.executors.BlockingAdaptiveExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrencyLimiter {

  private int threads = 1;
  private ExecutorService pool;
  private Executor executor;

  public void setThreads(int threads) {
    this.threads = threads;
  }

  public void setLimiter(Limiter<Void> limiter) {
    if(threads > 1) {
      pool = Executors.newFixedThreadPool(threads);
    } else {
      pool = Executors.newSingleThreadExecutor();
    }
    this.executor = new BlockingAdaptiveExecutor(limiter, pool);
  }

  public void execute(Runnable call) {
    executor.execute(call);
  }

  public void close() {
    if (pool == null) return;
    pool.shutdownNow();
    try {
      pool.awaitTermination(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      // do nothing
    }
  }

}
