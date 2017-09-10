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

import java.util.List;
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
import zipkin.TraceKeys;
import zipkin2.Span;
import zipkin.internal.V2SpanConverter;
import zipkin.internal.Util;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class Span2ConverterBenchmarks {
  Endpoint frontend = Endpoint.create("frontend", 127 << 24 | 1);
  Endpoint backend = Endpoint.builder()
    .serviceName("backend")
    .ipv4(192 << 24 | 168 << 16 | 99 << 8 | 101)
    .port(9000)
    .build();

  zipkin.Span shared = zipkin.Span.builder()
    .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
    .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
    .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
    .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
    .name("get")
    .timestamp(1472470996199000L)
    .duration(207000L)
    .addAnnotation(Annotation.create(1472470996199000L, Constants.CLIENT_SEND, frontend))
    .addAnnotation(Annotation.create(1472470996238000L, Constants.WIRE_SEND, frontend))
    .addAnnotation(Annotation.create(1472470996250000L, Constants.SERVER_RECV, backend))
    .addAnnotation(Annotation.create(1472470996350000L, Constants.SERVER_SEND, backend))
    .addAnnotation(Annotation.create(1472470996403000L, Constants.WIRE_RECV, frontend))
    .addAnnotation(Annotation.create(1472470996406000L, Constants.CLIENT_RECV, frontend))
    .addBinaryAnnotation(BinaryAnnotation.create(TraceKeys.HTTP_PATH, "/api", frontend))
    .addBinaryAnnotation(BinaryAnnotation.create(TraceKeys.HTTP_PATH, "/backend", backend))
    .addBinaryAnnotation(BinaryAnnotation.create("clnt/finagle.version", "6.45.0", frontend))
    .addBinaryAnnotation(BinaryAnnotation.create("srv/finagle.version", "6.44.0", backend))
    .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, frontend))
    .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, backend))
    .build();

  zipkin.Span server = zipkin.Span.builder()
    .traceIdHigh(Util.lowerHexToUnsignedLong("7180c278b62e8f6a"))
    .traceId(Util.lowerHexToUnsignedLong("216a2aea45d08fc9"))
    .parentId(Util.lowerHexToUnsignedLong("6b221d5bc9e6496c"))
    .id(Util.lowerHexToUnsignedLong("5b4185666d50f68b"))
    .name("get")
    .addAnnotation(Annotation.create(1472470996250000L, Constants.SERVER_RECV, backend))
    .addAnnotation(Annotation.create(1472470996350000L, Constants.SERVER_SEND, backend))
    .addBinaryAnnotation(BinaryAnnotation.create(TraceKeys.HTTP_PATH, "/backend", backend))
    .addBinaryAnnotation(BinaryAnnotation.create("srv/finagle.version", "6.44.0", backend))
    .addBinaryAnnotation(BinaryAnnotation.address(Constants.CLIENT_ADDR, frontend))
    .build();

  Span server2 = Span.newBuilder()
    .traceId("7180c278b62e8f6a216a2aea45d08fc9")
    .parentId("6b221d5bc9e6496c")
    .id("5b4185666d50f68b")
    .name("get")
    .kind(Span.Kind.SERVER)
    .shared(true)
    .localEndpoint(backend.toV2())
    .remoteEndpoint(frontend.toV2())
    .timestamp(1472470996250000L)
    .duration(100000L)
    .putTag(TraceKeys.HTTP_PATH, "/backend")
    .putTag("srv/finagle.version", "6.44.0")
    .build();

  @Benchmark public List<Span> fromSpan_splitShared() {
    return V2SpanConverter.fromSpan(shared);
  }

  @Benchmark public List<Span> fromSpan() {
    return V2SpanConverter.fromSpan(server);
  }

  @Benchmark public zipkin.Span toSpan() {
    return V2SpanConverter.toSpan(server2);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + Span2ConverterBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
