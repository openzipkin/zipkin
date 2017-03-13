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
package zipkin.benchmarks;

import com.google.common.io.ByteStreams;
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
import org.springframework.boot.actuate.metrics.buffer.CounterBuffers;
import org.springframework.boot.actuate.metrics.buffer.GaugeBuffers;
import zipkin.collector.CollectorMetrics;
import zipkin.collector.InMemoryCollectorMetrics;
import zipkin.server.ActuateCollectorMetrics;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Measurement(iterations = 80, time = 1)
@Warmup(iterations = 20, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class MetricsBenchmarks
{
  static final int LONG_SPAN = 5000;
  static final int MEDIUM_SPAN = 1000;
  static final int SHORT_SPAN = 500;

  private InMemoryCollectorMetrics inMemoryCollectorMetrics = new InMemoryCollectorMetrics();
  private ActuateCollectorMetrics actuateCollectorMetrics = new ActuateCollectorMetrics(new CounterBuffers(), new GaugeBuffers());

  @Benchmark
  public int incrementBytes_longSpans_inMemory() {
    return incrementBytes(inMemoryCollectorMetrics, LONG_SPAN);
  }

  @Benchmark
  public int incrementBytes_longSpans_Actuate() {
    return incrementBytes(actuateCollectorMetrics, LONG_SPAN);
  }

  @Benchmark
  public int incrementBytes_mediumSpans_inMemory() {
    return incrementBytes(inMemoryCollectorMetrics, MEDIUM_SPAN);
  }

  @Benchmark
  public int incrementBytes_mediumSpans_Actuate() {
    return incrementBytes(actuateCollectorMetrics, MEDIUM_SPAN);
  }

  @Benchmark
  public int incrementBytes_shortSpans_inMemory() {
    return incrementBytes(inMemoryCollectorMetrics, SHORT_SPAN);
  }

  @Benchmark
  public int incrementBytes_shortSpans_Actuate() {
    return incrementBytes(actuateCollectorMetrics, SHORT_SPAN);
  }

  private int incrementBytes(CollectorMetrics collectorMetrics, int bytes)
  {
    collectorMetrics.incrementBytes(bytes);
    return bytes;
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException
  {
    Options opt = new OptionsBuilder()
        .include(".*" + MetricsBenchmarks.class.getSimpleName() + ".*")
        .threads(40)
        .build();

    new Runner(opt).run();
  }


  static byte[] read(String resource) {
    try {
      return ByteStreams.toByteArray(CodecBenchmarks.class.getResourceAsStream(resource));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
