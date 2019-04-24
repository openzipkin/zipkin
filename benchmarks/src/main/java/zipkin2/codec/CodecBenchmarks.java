/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

/**
 * The {@link SpanBytesEncoder bundled java codec} aims to be both small in size (i.e. does not
 * significantly increase the size of zipkin's jar), and efficient. It may not always be fastest,
 * but we should try to keep it competitive.
 *
 * <p>Note that the wire benchmarks use their structs, not ours. This will result in more efficient
 * writes as there's no hex codec of IDs, stringifying of IPs etc. A later change could do that, but
 * it likely still going to be more efficient than our dependency-free codec. This means in cases
 * where extra dependencies are ok (such as our server), we could consider using wire.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class CodecBenchmarks {
  static final byte[] clientSpanJsonV2 = read("/zipkin2-client.json");
  static final Span clientSpan = SpanBytesDecoder.JSON_V2.decodeOne(clientSpanJsonV2);
  static final byte[] clientSpanProto3 = SpanBytesEncoder.PROTO3.encode(clientSpan);
  static final zipkin2.proto3.Span clientSpan_wire;
  static final List<Span> tenClientSpans = Collections.nCopies(10, clientSpan);
  static final byte[] tenClientSpansJsonV2 = SpanBytesEncoder.JSON_V2.encodeList(tenClientSpans);
  static final Kryo kryo = new Kryo();
  static final byte[] clientSpanSerialized;

  static {
    kryo.register(Span.class, new JavaSerializer());
    Output output = new Output(4096);
    kryo.writeObject(output, clientSpan);
    output.flush();
    clientSpanSerialized = output.getBuffer();
    try {
      clientSpan_wire = zipkin2.proto3.Span.ADAPTER.decode(clientSpanProto3);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /** manually implemented with json so not as slow as normal java */
  @Benchmark
  public Span readClientSpan_kryo() {
    return kryo.readObject(new Input(clientSpanSerialized), Span.class);
  }

  @Benchmark
  public byte[] writeClientSpan_kryo() {
    Output output = new Output(clientSpanSerialized.length);
    kryo.writeObject(output, clientSpan);
    output.flush();
    return output.getBuffer();
  }

  @Benchmark
  public Span readClientSpan_json() {
    return SpanBytesDecoder.JSON_V2.decodeOne(clientSpanJsonV2);
  }

  @Benchmark
  public Span readClientSpan_proto3() {
    return SpanBytesDecoder.PROTO3.decodeOne(clientSpanProto3);
  }

  @Benchmark
  public zipkin2.proto3.Span readClientSpan_proto3_wire() throws Exception {
    return zipkin2.proto3.Span.ADAPTER.decode(clientSpanProto3);
  }

  @Benchmark
  public List<Span> readTenClientSpans_json() {
    return SpanBytesDecoder.JSON_V2.decodeList(tenClientSpansJsonV2);
  }

  @Benchmark
  public byte[] writeClientSpan_json() {
    return SpanBytesEncoder.JSON_V2.encode(clientSpan);
  }

  @Benchmark
  public byte[] writeTenClientSpans_json() {
    return SpanBytesEncoder.JSON_V2.encodeList(tenClientSpans);
  }

  @Benchmark
  public byte[] writeClientSpan_json_v1() {
    return SpanBytesEncoder.JSON_V1.encode(clientSpan);
  }

  @Benchmark
  public byte[] writeTenClientSpans_json_v1() {
    return SpanBytesEncoder.JSON_V1.encodeList(tenClientSpans);
  }

  @Benchmark
  public byte[] writeClientSpan_proto3() {
    return SpanBytesEncoder.PROTO3.encode(clientSpan);
  }

  @Benchmark
  public byte[] writeClientSpan_proto3_wire() {
    return clientSpan_wire.encode();
  }

  static final byte[] chineseSpanJsonV2 = read("/zipkin2-chinese.json");
  static final Span chineseSpan = SpanBytesDecoder.JSON_V2.decodeOne(chineseSpanJsonV2);
  static final zipkin2.proto3.Span chineseSpan_wire;
  static final byte[] chineseSpanProto3 = SpanBytesEncoder.PROTO3.encode(chineseSpan);

  static {
    try {
      chineseSpan_wire = zipkin2.proto3.Span.ADAPTER.decode(chineseSpanProto3);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Benchmark
  public Span readChineseSpan_json() {
    return SpanBytesDecoder.JSON_V2.decodeOne(chineseSpanJsonV2);
  }

  @Benchmark
  public Span readChineseSpan_proto3() {
    return SpanBytesDecoder.PROTO3.decodeOne(chineseSpanProto3);
  }

  @Benchmark
  public zipkin2.proto3.Span readChineseSpan_proto3_wire() throws Exception {
    return zipkin2.proto3.Span.ADAPTER.decode(chineseSpanProto3);
  }

  @Benchmark
  public byte[] writeChineseSpan_json() {
    return SpanBytesEncoder.JSON_V2.encode(chineseSpan);
  }

  @Benchmark
  public byte[] writeChineseSpan_proto3() {
    return SpanBytesEncoder.PROTO3.encode(chineseSpan);
  }

  @Benchmark
  public byte[] writeChineseSpan_proto3_wire() {
    return chineseSpan_wire.encode();
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
