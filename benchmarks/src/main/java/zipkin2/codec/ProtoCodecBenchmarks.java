/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

import com.google.common.io.Resources;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
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
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.Span;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class ProtoCodecBenchmarks {

  static final byte[] clientSpanJsonV2 = read("/zipkin2-client.json");
  static final Span clientSpan = SpanBytesDecoder.JSON_V2.decodeOne(clientSpanJsonV2);

  // Assume a message is 1000 spans (which is a high number for as this is per-node-second)
  static final List<Span> spans = Collections.nCopies(1000, clientSpan);
  static final byte[] encodedBytes = SpanBytesEncoder.PROTO3.encodeList(spans);

  private ByteBuf encodedBuf;

  @Setup
  public void setup() {
    encodedBuf = PooledByteBufAllocator.DEFAULT.buffer(encodedBytes.length);
    encodedBuf.writeBytes(encodedBytes);
  }

  @TearDown
  public void tearDown() {
    encodedBuf.release();
  }

  @Benchmark
  public List<Span> bytes_zipkinDecoder() {
    return SpanBytesDecoder.PROTO3.decodeList(encodedBytes);
  }

  @Benchmark
  public List<Span> bytes_protobufDecoder() {
    return ProtobufSpanDecoder.decodeList(encodedBytes);
  }

  @Benchmark
  public List<Span> bytes_wireDecoder() {
    return WireSpanDecoder.decodeList(encodedBytes);
  }

  @Benchmark
  public List<Span> bytebuffer_zipkinDecoder() {
    return SpanBytesDecoder.PROTO3.decodeList(encodedBuf.nioBuffer());
  }

  @Benchmark
  public List<Span> bytebuffer_protobufDecoder() {
    return ProtobufSpanDecoder.decodeList(encodedBuf.nioBuffer());
  }

  @Benchmark
  public List<Span> bytebuffer_wireDecoder() {
    return WireSpanDecoder.decodeList(encodedBuf.nioBuffer());
  }

  // Convenience main entry-point
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
      .include(".*" + ProtoCodecBenchmarks.class.getSimpleName())
      .addProfiler("gc")
      .build();

    new Runner(opt).run();
  }

  static byte[] read(String resource) {
    try {
      return Resources.toByteArray(Resources.getResource(CodecBenchmarks.class, resource));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
