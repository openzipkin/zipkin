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
package zipkin.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin.Sampler;

/**
 * <p>Zipkin v1 uses before-the-fact sampling. This means that the decision to keep or drop the
 * trace is made before any work is measured, or annotations are added. As such, the input parameter
 * to zipkin v1 samplers is the trace id (64-bit random number).
 *
 * <p>This only tests performance of various approaches against each-other. This doesn't test if the
 * same trace id is consistently sampled or not, or how close to the retention percentage the
 * samplers get.
 *
 * <p>While random sampling gives a better statistical average across all spans, it's less useful
 * than the ability to see end to end interrelated work, such as a from a specific user, or messages
 * blocking others in a queue. More sampling patterns are expected in OpenTracing and Zipkin v2.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class SamplerBenchmarks {

  /**
   * Sample rate is a percentage expressed as a float. So, 0.001 is 0.1% (let one in a 1000nd pass).
   * Zero effectively disables tracing.
   *
   * <p>Here are default sample rates from actual implementations:
   * <pre>
   * <ul>
   *   <li>Finagle Scala Tracer: 0.001</li>
   *   <li>Finagle Ruby Tracer: 0.001</li>
   *   <li>Brave Java Tracer: 1.0</li>
   *   <li>Zipkin Collector: 1.0</li>
   * </ul>
   * </pre>
   */
  static final float SAMPLE_RATE = 0.001f;

  @State(Scope.Benchmark)
  public static class Args {

    /**
     * Arguments include the most negative number, and an arbitrary one.
     */
    // JMH doesn't support Long.MIN_VALUE or hex references, hence the long form literals.
    @Param({"-9223372036854775808", "1234567890987654321"})
    long traceId;
  }

  /**
   * This measures the trace id sampler provided with zipkin-java
   */
  @Benchmark
  public boolean traceIdSampler(Args args) {
    return TRACE_ID_SAMPLER.isSampled(args.traceId);
  }

  static final Sampler TRACE_ID_SAMPLER = Sampler.create(SAMPLE_RATE);

  /**
   * Zipkin collector's AdjustableGlobalSampler compares the absolute value of the trace id against
   * a product of the sample rate. It defends against the most negative number in two's complement.
   *
   * <p>Collectors receive a trace incrementally and repeat the sampling decision for each part.
   * This means that consistency against a trace id is a primary feature of this algorithm.
   *
   * <p>See https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/src/main/scala/com/twitter/zipkin/collector/sampler/AdjustableGlobalSampler.scala#L55
   */
  @Benchmark
  public boolean compareTraceId_mostNegativeNumberDefense(Args args) {
    long traceId = args.traceId;
    // The absolute value of Long.MIN_VALUE is larger than a long, so returns Math.abs identity.
    // This converts to MAX_VALUE to avoid comparing the sample rate against a negative number.
    long t = traceId == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(traceId);
    return t < Long.MAX_VALUE * SAMPLE_RATE; // Constant expression for readability
  }

  /**
   * Finagle's scala sampler samples using modulo 10000 arithmetic, which allows a minimum sample
   * rate of 0.01%.
   *
   * <p>This function is only invoked once per trace, propagating the result downstream as a field
   * called sampled. This means it is designed for instrumented entry-points.
   *
   * <p>Trace id collision was noticed in practice in the Twitter front-end cluster. A random salt
   * feature was added to defend against nodes in the same cluster sampling exactly the same subset
   * of trace ids. The goal was full 64-bit coverage of traceIds on multi-host deployments.
   *
   * <p>See https://github.com/twitter/finagle/blob/develop/finagle-zipkin/src/main/scala/com/twitter/finagle/zipkin/thrift/Sampler.scala#L68
   */
  @Benchmark
  public boolean compareTraceId_modulo10000_salted(Args args) {
    long traceId = args.traceId;
    long t = Math.abs(traceId ^ SALT);
    // Minimum sample rate is one in 10000, or 0.01% of traces
    return t % 10000 < SAMPLE_RATE * 10000; // Constant expression for readability
  }

  static final long SALT = new Random().nextLong();

  /**
   * Finagle's ruby sampler gets a random number and compares that against the sample rate.
   *
   * <p>This function is only invoked once per trace, propagating the result downstream as a field
   * called sampled. This means it is designed for instrumented entry-points.
   *
   * <p>See https://github.com/twitter/finagle/blob/develop/finagle-thrift/src/main/ruby/lib/finagle-thrift/trace.rb#L135
   */
  @Benchmark
  public boolean compareRandomNumber(Args args) {
    return RNG.nextFloat() < SAMPLE_RATE; // notice trace id is not used
  }

  final Random RNG = new Random();

  /**
   * Brave's FixedSampleRateTraceFilter uses a shared counter to guarantee an sample ratio. This
   * approach cannot guarantee a consistent decision, as it doesn't use the trace id. Depending on
   * implementation, this may or may not be a problem. For example, this strictly periodic approach
   * could be problematic for systems that process spans that are cyclic / repetitive.
   *
   * <p>Note: Brave 3.4+ no longer uses this approach.
   *
   * <p>See https://github.com/openzipkin/brave/blob/brave-3.3.0/brave-core/src/main/java/com/github/kristofa/brave/FixedSampleRateTraceFilter.java
   */
  @Benchmark
  public boolean compareCounter(Args args) {
    final int value = COUNTER.incrementAndGet(); // notice trace id is not used
    if (value >= SAMPLE_RATIO) {
      synchronized (COUNTER) {
        if (COUNTER.get() >= SAMPLE_RATIO) {
          COUNTER.set(0);
          return true;
        }
      }
    }
    return false;
  }

  private final int SAMPLE_RATIO = 1000; // brave uses ratio as opposed to percentage
  final AtomicInteger COUNTER = new AtomicInteger();

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + SamplerBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}
