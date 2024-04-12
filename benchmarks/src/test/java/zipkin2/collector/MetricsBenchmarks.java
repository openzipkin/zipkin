/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
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
import zipkin2.server.internal.MicrometerCollectorMetrics;

@Measurement(iterations = 80, time = 1)
@Warmup(iterations = 20, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class MetricsBenchmarks {
  static final int LONG_SPAN = 5000;
  static final int MEDIUM_SPAN = 1000;
  static final int SHORT_SPAN = 500;
  private MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
  private InMemoryCollectorMetrics inMemoryCollectorMetrics = new InMemoryCollectorMetrics()
    .forTransport("jmh");
  private MicrometerCollectorMetrics micrometerCollectorMetrics =
    new MicrometerCollectorMetrics(registry)
      .forTransport("jmh");

  @Benchmark
  public int incrementBytes_longSpans_inMemory() {
    return incrementBytes(inMemoryCollectorMetrics, LONG_SPAN);
  }

  @Benchmark
  public int incrementBytes_longSpans_Actuate() {
    return incrementBytes(micrometerCollectorMetrics, LONG_SPAN);
  }

  @Benchmark
  public int incrementBytes_mediumSpans_inMemory() {
    return incrementBytes(inMemoryCollectorMetrics, MEDIUM_SPAN);
  }

  @Benchmark
  public int incrementBytes_mediumSpans_Actuate() {
    return incrementBytes(micrometerCollectorMetrics, MEDIUM_SPAN);
  }

  @Benchmark
  public int incrementBytes_shortSpans_inMemory() {
    return incrementBytes(inMemoryCollectorMetrics, SHORT_SPAN);
  }

  @Benchmark
  public int incrementBytes_shortSpans_Actuate() {
    return incrementBytes(micrometerCollectorMetrics, SHORT_SPAN);
  }

  private int incrementBytes(CollectorMetrics collectorMetrics, int bytes) {
    collectorMetrics.incrementBytes(bytes);
    return bytes;
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + MetricsBenchmarks.class.getSimpleName() + ".*")
      .threads(40)
      .build();

    new Runner(opt).run();
  }
}
