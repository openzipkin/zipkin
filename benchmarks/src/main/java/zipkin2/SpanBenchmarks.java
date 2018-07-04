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
package zipkin2;

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
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class SpanBenchmarks {
  static final Endpoint FRONTEND =
    Endpoint.newBuilder().serviceName("frontend").ip("127.0.0.1").build();
  static final Endpoint BACKEND =
    Endpoint.newBuilder().serviceName("backend").ip("192.168.99.101").port(9000).build();

  final Span.Builder sharedBuilder;

  public SpanBenchmarks() {
    sharedBuilder = buildClientSpan().toBuilder();
  }

  static final String traceIdHex = "86154a4ba6e91385";
  static final String spanIdHex = "4d1e00c0db9010db";

  @Benchmark
  public Span buildClientSpan() {
    return buildClientSpan(Span.newBuilder());
  }

  static Span buildClientSpan(Span.Builder builder) {
    return builder
      .traceId(traceIdHex)
      .parentId(traceIdHex)
      .id(spanIdHex)
      .name("get")
      .kind(Span.Kind.CLIENT)
      .localEndpoint(FRONTEND)
      .remoteEndpoint(BACKEND)
      .timestamp(1472470996199000L)
      .duration(207000L)
      .addAnnotation(1472470996238000L, "ws")
      .addAnnotation(1472470996403000L, "wr")
      .putTag("http.path", "/api")
      .putTag("clnt/finagle.version", "6.45.0")
      .build();
  }

  @Benchmark
  public Span buildClientSpan_clear() {
    return buildClientSpan(sharedBuilder.clear());
  }

  @Benchmark
  public Span buildClientSpan_clone() {
    return sharedBuilder.clone().build();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + SpanBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
