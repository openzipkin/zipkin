/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static zipkin2.storage.cassandra.internal.Resources.resourceToString;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class JsonCodecBenchmarks {
  static final MoshiSpanDecoder MOSHI = MoshiSpanDecoder.create();

  static final byte[] clientSpanJsonV2 = resourceToString("/zipkin2-client.json").getBytes(UTF_8);
  static final Span clientSpan = SpanBytesDecoder.JSON_V2.decodeOne(clientSpanJsonV2);

  // Assume a message is 1000 spans (which is a high number for as this is per-node-second)
  static final List<Span> spans = Collections.nCopies(1000, clientSpan);
  static final byte[] encodedBytes = SpanBytesEncoder.JSON_V2.encodeList(spans);

  private ByteBuf encodedBuf;

  @Setup public void setup() {
    encodedBuf = PooledByteBufAllocator.DEFAULT.buffer(encodedBytes.length);
    encodedBuf.writeBytes(encodedBytes);
  }

  @TearDown public void tearDown() {
    encodedBuf.release();
  }

  @Benchmark public List<Span> bytes_jacksonDecoder() {
    return JacksonSpanDecoder.decodeList(encodedBytes);
  }

  @Benchmark public List<Span> bytes_moshiDecoder() {
    return MOSHI.decodeList(encodedBytes);
  }

  @Benchmark public List<Span> bytes_zipkinDecoder() {
    return SpanBytesDecoder.JSON_V2.decodeList(encodedBytes);
  }

  @Benchmark public List<Span> bytebuffer_jacksonDecoder() {
    return JacksonSpanDecoder.decodeList(encodedBuf.nioBuffer());
  }

  @Benchmark public List<Span> bytebuffer_moshiDecoder() {
    return MOSHI.decodeList(encodedBuf.nioBuffer());
  }

  @Benchmark public List<Span> bytebuffer_zipkinDecoder() {
    return SpanBytesDecoder.JSON_V2.decodeList(encodedBuf.nioBuffer());
  }

  // Convenience main entry-point
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
      .include(".*" + JsonCodecBenchmarks.class.getSimpleName() + ".*")
      .addProfiler("gc")
      .build();

    new Runner(opt).run();
  }
}
