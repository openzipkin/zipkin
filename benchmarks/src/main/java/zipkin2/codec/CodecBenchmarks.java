/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.codec;

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

import static java.nio.charset.StandardCharsets.UTF_8;
import static zipkin2.storage.cassandra.internal.Resources.resourceToString;

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
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class CodecBenchmarks {
  static final byte[] clientSpanJsonV2 = resourceToString("/zipkin2-client.json").getBytes(UTF_8);
  static final Span clientSpan = SpanBytesDecoder.JSON_V2.decodeOne(clientSpanJsonV2);
  static final byte[] clientSpanJsonV1 = SpanBytesEncoder.JSON_V1.encode(clientSpan);
  static final byte[] clientSpanProto3 = SpanBytesEncoder.PROTO3.encode(clientSpan);
  static final byte[] clientSpanThrift = SpanBytesEncoder.THRIFT.encode(clientSpan);
  static final List<Span> tenClientSpans = Collections.nCopies(10, clientSpan);
  static final byte[] tenClientSpansJsonV2 = SpanBytesEncoder.JSON_V2.encodeList(tenClientSpans);

  @Benchmark
  public Span decodeClientSpan_JSON_V1() {
    return SpanBytesDecoder.JSON_V1.decodeOne(clientSpanJsonV1);
  }

  @Benchmark
  public Span decodeClientSpan_JSON_V2() {
    return SpanBytesDecoder.JSON_V2.decodeOne(clientSpanJsonV2);
  }

  @Benchmark
  public Span decodeClientSpan_PROTO3() {
    return SpanBytesDecoder.PROTO3.decodeOne(clientSpanProto3);
  }

  @Benchmark
  public Span decodeClientSpan_THRIFT() {
    return SpanBytesDecoder.THRIFT.decodeOne(clientSpanThrift);
  }

  @Benchmark
  public int sizeInBytesClientSpan_JSON_V2() {
    return SpanBytesEncoder.JSON_V2.sizeInBytes(clientSpan);
  }

  @Benchmark
  public int sizeInBytesClientSpan_JSON_V1() {
    return SpanBytesEncoder.JSON_V1.sizeInBytes(clientSpan);
  }

  @Benchmark
  public int sizeInBytesClientSpan_PROTO3() {
    return SpanBytesEncoder.PROTO3.sizeInBytes(clientSpan);
  }

  @Benchmark
  public int sizeInBytesClientSpan_THRIFT() {
    return SpanBytesEncoder.THRIFT.sizeInBytes(clientSpan);
  }

  @Benchmark
  public byte[] writeClientSpan_JSON_V2() {
    return SpanBytesEncoder.JSON_V2.encode(clientSpan);
  }

  @Benchmark
  public byte[] writeClientSpan_JSON_V1() {
    return SpanBytesEncoder.JSON_V1.encode(clientSpan);
  }

  @Benchmark
  public byte[] writeClientSpan_PROTO3() {
    return SpanBytesEncoder.PROTO3.encode(clientSpan);
  }

  @Benchmark
  public byte[] writeClientSpan_THRIFT() {
    return SpanBytesEncoder.THRIFT.encode(clientSpan);
  }

  @Benchmark
  public List<Span> decodeTenClientSpans_JSON_V2() {
    return SpanBytesDecoder.JSON_V2.decodeList(tenClientSpansJsonV2);
  }

  @Benchmark
  public byte[] writeTenClientSpans_JSON_V2() {
    return SpanBytesEncoder.JSON_V2.encodeList(tenClientSpans);
  }

  static final byte[] chineseSpanJsonV2 = resourceToString("/zipkin2-chinese.json").getBytes(UTF_8);
  static final Span chineseSpan = SpanBytesDecoder.JSON_V2.decodeOne(chineseSpanJsonV2);
  static final byte[] chineseSpanProto3 = SpanBytesEncoder.PROTO3.encode(chineseSpan);
  static final byte[] chineseSpanJsonV1 = SpanBytesEncoder.JSON_V1.encode(chineseSpan);
  static final byte[] chineseSpanThrift = SpanBytesEncoder.THRIFT.encode(chineseSpan);

  @Benchmark
  public Span decodeChineseSpan_JSON_V1() {
    return SpanBytesDecoder.JSON_V1.decodeOne(chineseSpanJsonV1);
  }

  @Benchmark
  public Span decodeChineseSpan_JSON_V2() {
    return SpanBytesDecoder.JSON_V2.decodeOne(chineseSpanJsonV2);
  }

  @Benchmark
  public Span decodeChineseSpan_PROTO3() {
    return SpanBytesDecoder.PROTO3.decodeOne(chineseSpanProto3);
  }

  @Benchmark
  public Span decodeChineseSpan_THRIFT() {
    return SpanBytesDecoder.THRIFT.decodeOne(chineseSpanThrift);
  }

  @Benchmark
  public byte[] writeChineseSpan_JSON_V2() {
    return SpanBytesEncoder.JSON_V2.encode(chineseSpan);
  }

  @Benchmark
  public byte[] writeChineseSpan_JSON_V1() {
    return SpanBytesEncoder.JSON_V1.encode(chineseSpan);
  }

  @Benchmark
  public byte[] writeChineseSpan_PROTO3() {
    return SpanBytesEncoder.PROTO3.encode(chineseSpan);
  }

  @Benchmark
  public byte[] writeChineseSpan_THRIFT() {
    return SpanBytesEncoder.THRIFT.encode(chineseSpan);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + CodecBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
