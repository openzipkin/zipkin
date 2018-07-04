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
package zipkin2.codec;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.Collections;
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
import zipkin2.Span;

import static zipkin2.proto3.Span.parseFrom;

/**
 * The {@link SpanBytesEncoder bundled java codec} aims to be both small in size (i.e. does not
 * significantly increase the size of zipkin's jar), and efficient. It may not always be fastest,
 * but we should try to keep it competitive.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class CodecBenchmarks {
  static final byte[] zipkin2Json = read("/zipkin2-client.json");
  static final Span zipkin2 = SpanBytesDecoder.JSON_V2.decodeOne(zipkin2Json);
  static final byte[] zipkin2Proto3 = SpanBytesEncoder.PROTO3.encode(zipkin2);
  static final List<Span> tenSpan2s = Collections.nCopies(10, zipkin2);
  static final byte[] tenSpan2sJson = SpanBytesEncoder.JSON_V2.encodeList(tenSpan2s);
  static final Kryo kryo = new Kryo();
  static final byte[] zipkin2Serialized;

  static {
    kryo.register(Span.class, new JavaSerializer());
    Output output = new Output(4096);
    kryo.writeObject(output, zipkin2);
    output.flush();
    zipkin2Serialized = output.getBuffer();
  }

  /** manually implemented with json so not as slow as normal java */
  @Benchmark
  public Span readClientSpan_java() {
    return kryo.readObject(new Input(zipkin2Serialized), Span.class);
  }

  @Benchmark
  public byte[] writeClientSpan_java() {
    Output output = new Output(zipkin2Serialized.length);
    kryo.writeObject(output, zipkin2);
    output.flush();
    return output.getBuffer();
  }

  @Benchmark
  public Span readClientSpan_json() {
    return SpanBytesDecoder.JSON_V2.decodeOne(zipkin2Json);
  }

  @Benchmark
  public Span readClientSpan_proto3() {
    return SpanBytesDecoder.PROTO3.decodeOne(zipkin2Proto3);
  }

  @Benchmark
  public zipkin2.proto3.Span readClientSpan_proto3_protobuf() throws Exception {
    return parseFrom(zipkin2Proto3);
  }

  @Benchmark
  public List<Span> readTenClientSpans_json() {
    return SpanBytesDecoder.JSON_V2.decodeList(tenSpan2sJson);
  }

  @Benchmark
  public byte[] writeClientSpan_json() {
    return SpanBytesEncoder.JSON_V2.encode(zipkin2);
  }

  @Benchmark
  public byte[] writeTenClientSpans_json() {
    return SpanBytesEncoder.JSON_V2.encodeList(tenSpan2s);
  }

  @Benchmark
  public byte[] writeClientSpan_json_legacy() {
    return SpanBytesEncoder.JSON_V1.encode(zipkin2);
  }

  @Benchmark
  public byte[] writeTenClientSpans_json_legacy() {
    return SpanBytesEncoder.JSON_V1.encodeList(tenSpan2s);
  }

  @Benchmark
  public byte[] writeClientSpan_proto3() {
    return SpanBytesEncoder.PROTO3.encode(zipkin2);
  }

  static final byte[] zipkin2JsonChinese = read("/zipkin2-chinese.json");
  static final Span zipkin2Chinese = SpanBytesDecoder.JSON_V2.decodeOne(zipkin2JsonChinese);
  static final byte[] zipkin2Proto3Chinese = SpanBytesEncoder.PROTO3.encode(zipkin2Chinese);

  @Benchmark
  public Span readChineseSpan_json() {
    return SpanBytesDecoder.JSON_V2.decodeOne(zipkin2JsonChinese);
  }

  @Benchmark
  public Span readChineseSpan_proto3() {
    return SpanBytesDecoder.PROTO3.decodeOne(zipkin2Proto3Chinese);
  }

  @Benchmark
  public zipkin2.proto3.Span readChineseSpan_proto3_protobuf() throws Exception {
    return parseFrom(zipkin2Proto3Chinese);
  }

  @Benchmark
  public byte[] writeChineseSpan_json() {
    return SpanBytesEncoder.JSON_V2.encode(zipkin2Chinese);
  }

  @Benchmark
  public byte[] writeChineseSpan_proto3() {
    return SpanBytesEncoder.PROTO3.encode(zipkin2Chinese);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + CodecBenchmarks.class.getSimpleName())
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
