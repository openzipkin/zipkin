/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(2)
public class DelayLimiterBenchmarks {

  final Random rng = new Random();
  final DelayLimiter<Long> limiter = DelayLimiter.newBuilder()
    .ttl(1L, TimeUnit.HOURS) // legacy default from Cassandra
    .cardinality(5 * 4000) // Ex. 5 site tags with cardinality 4000 each
    .build();

  @Benchmark public boolean shouldInvoke_randomData() {
    return limiter.shouldInvoke(rng.nextLong());
  }

  @Benchmark public boolean shouldInvoke_sameData() {
    return limiter.shouldInvoke(1L);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + DelayLimiterBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
