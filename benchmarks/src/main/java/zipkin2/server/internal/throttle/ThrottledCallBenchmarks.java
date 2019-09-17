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
import com.netflix.concurrency.limits.limit.FixedLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.Call;
import zipkin2.Callback;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(2)
public class ThrottledCallBenchmarks {
  ExecutorService fakeCallExecutor = Executors.newSingleThreadExecutor();
  ExecutorService executor = Executors.newSingleThreadExecutor();
  ThrottledCall call;

  @Setup public void setup() {
    executor = Executors.newSingleThreadExecutor();
    fakeCallExecutor = Executors.newSingleThreadExecutor();
    SimpleLimiter<Void> limiter = SimpleLimiter.newBuilder().limit(FixedLimit.of(1)).build();
    LimiterMetrics metrics = new LimiterMetrics(NoopMeterRegistry.get());
    Predicate<Throwable> isOverCapacity = RejectedExecutionException.class::isInstance;
    call =
      new ThrottledCall(new FakeCall(fakeCallExecutor), executor, limiter, metrics, isOverCapacity);
  }

  @TearDown public void tearDown() {
    executor.shutdown();
    fakeCallExecutor.shutdown();
  }

  @Benchmark public Object execute() throws IOException {
    return call.clone().execute();
  }

  @Benchmark public void execute_overCapacity() throws IOException {
    ThrottledCall overCapacity = (ThrottledCall) call.clone();
    ((FakeCall) overCapacity.delegate).overCapacity = true;

    try {
      overCapacity.execute();
    } catch (RejectedExecutionException e) {
      assert e == OVER_CAPACITY;
    }
  }

  @Benchmark public void execute_throttled() throws IOException {
    call.limiter.acquire(null); // capacity is 1, so this will overdo it.
    call.clone().execute();
  }

  static final RejectedExecutionException OVER_CAPACITY = new RejectedExecutionException();

  static final class FakeCall extends Call.Base<Void> {
    final Executor executor;
    boolean overCapacity = false;

    FakeCall(Executor executor) {
      this.executor = executor;
    }

    @Override public Void doExecute() throws IOException {
      if (overCapacity) throw OVER_CAPACITY;
      return null;
    }

    @Override public void doEnqueue(Callback<Void> callback) {
      executor.execute(() -> {
        if (overCapacity) {
          callback.onError(OVER_CAPACITY);
        } else {
          callback.onSuccess(null);
        }
      });
    }

    @Override public FakeCall clone() {
      return new FakeCall(executor);
    }
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + ThrottledCallBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
