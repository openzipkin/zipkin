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

import com.google.common.io.ByteStreams;
import java.io.IOException;
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
import zipkin.Codec;
import zipkin.Span;

/**
 * This compares the speed of the bundled java codec with the approach used in the scala
 * implementation. Re-run this benchmark when changing internals of {@link zipkin.Codec}.
 *
 * <p>The {@link zipkin.Codec bundled java codec} aims to be both small in size (i.e. does not
 * significantly increase the size of zipkin's jar), and efficient. It may not always be fastest,
 * but we should try to keep it competitive.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Threads(1)
public class CodecBenchmarks {

  static final byte[] localSpanJson = read("/span-local.json");
  static final Span localSpan = Codec.JSON.readSpan(localSpanJson);
  static final byte[] localSpanThrift = Codec.THRIFT.writeSpan(localSpan);

  @Benchmark
  public Span readLocalSpan_json_java() {
    return Codec.JSON.readSpan(localSpanJson);
  }

  @Benchmark
  public Span readLocalSpan_thrift_java() {
    return Codec.THRIFT.readSpan(localSpanThrift);
  }

  @Benchmark
  public byte[] writeLocalSpan_json_java() {
    return Codec.JSON.writeSpan(localSpan);
  }

  @Benchmark
  public byte[] writeLocalSpan_thrift_java() {
    return Codec.THRIFT.writeSpan(localSpan);
  }

  static final byte[] clientSpanJson = read("/span-client.json");
  static final Span clientSpan = Codec.JSON.readSpan(clientSpanJson);
  static final byte[] clientSpanThrift = Codec.THRIFT.writeSpan(clientSpan);

  @Benchmark
  public Span readClientSpan_json_java() {
    return Codec.JSON.readSpan(clientSpanJson);
  }

  @Benchmark
  public Span readClientSpan_thrift_java() {
    return Codec.THRIFT.readSpan(clientSpanThrift);
  }

  @Benchmark
  public byte[] writeClientSpan_json_java() {
    return Codec.JSON.writeSpan(clientSpan);
  }

  @Benchmark
  public byte[] writeClientSpan_thrift_java() {
    return Codec.THRIFT.writeSpan(clientSpan);
  }

  static final byte[] rpcSpanJson = read("/span-client.json");
  static final Span rpcSpan = Codec.JSON.readSpan(rpcSpanJson);
  static final byte[] rpcSpanThrift = Codec.THRIFT.writeSpan(rpcSpan);

  @Benchmark
  public Span readRpcSpan_json_java() {
    return Codec.JSON.readSpan(rpcSpanJson);
  }

  @Benchmark
  public Span readRpcSpan_thrift_java() {
    return Codec.THRIFT.readSpan(rpcSpanThrift);
  }

  @Benchmark
  public byte[] writeRpcSpan_json_java() {
    return Codec.JSON.writeSpan(rpcSpan);
  }

  @Benchmark
  public byte[] writeRpcSpan_thrift_java() {
    return Codec.THRIFT.writeSpan(rpcSpan);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + CodecBenchmarks.class.getSimpleName() + ".*")
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
