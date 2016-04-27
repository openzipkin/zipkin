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
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class SpanBenchmarks {
  // endpoints are almost always cached, so caching here to record more accurate performance
  static final Endpoint web = Endpoint.create("web", 124 << 24 | 13 << 16 | 90 << 8 | 3);
  static final Endpoint app = Endpoint.create("app", 172 << 24 | 17 << 16 | 2, 8080);
  static final Endpoint db = Endpoint.create("db", 172 << 24 | 17 << 16 | 2, 3306);

  @Benchmark
  public Span buildLocalSpan() {
    return new Span.Builder()
        .traceId(1L)
        .id(1L)
        .name("work")
        .timestamp(1444438900948000L)
        .duration(31000L)
        .addBinaryAnnotation(BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "worker", app))
        .build();
  }

  @Benchmark
  public Span buildClientOnlySpan() {
    return new Span.Builder()
        .traceId(1L)
        .id(1L)
        .name("")
        .timestamp(1444438900948000L)
        .duration(31000L)
        .addAnnotation(Annotation.create(1444438900948000L, Constants.CLIENT_SEND, app))
        .addAnnotation(Annotation.create(1444438900979000L, Constants.CLIENT_RECV, app))
        .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, db))
        .build();
  }

  @Benchmark
  public Span buildRpcSpan() {
    return new Span.Builder() // web calls app
        .traceId(1L)
        .id(2L)
        .parentId(1L)
        .name("get")
        .timestamp(1444438900941000L)
        .duration(77000L)
        .addAnnotation(Annotation.create(1444438900941000L, Constants.CLIENT_SEND, web))
        .addAnnotation(Annotation.create(1444438900947000L, Constants.SERVER_RECV, app))
        .addAnnotation(Annotation.create(1444438901017000L, Constants.SERVER_SEND, app))
        .addAnnotation(Annotation.create(1444438901018000L, Constants.CLIENT_RECV, web))
        .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, app))
        .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, web))
        .build();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + SpanBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}
