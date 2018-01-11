/**
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
package zipkin.benchmarks;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.io.ByteStreams;
import com.twitter.zipkin.thriftjava.Annotation;
import com.twitter.zipkin.thriftjava.BinaryAnnotation;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
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
import zipkin.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;

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
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class CodecBenchmarks {
  static final TBinaryProtocol.Factory TBINARY_PROTOCOL_FACTORY = new TBinaryProtocol.Factory();

  static final byte[] localSpanJson = read("/span-local.json");
  static final zipkin.Span localSpan = Codec.JSON.readSpan(localSpanJson);
  static final byte[] localSpanThrift = Codec.THRIFT.writeSpan(localSpan);
  static final com.twitter.zipkin.thriftjava.Span localSpanLibThrift = deserialize(localSpanThrift);

  @Benchmark
  public zipkin.Span readLocalSpan_json_zipkin() {
    return Codec.JSON.readSpan(localSpanJson);
  }

  @Benchmark
  public zipkin.Span readLocalSpan_thrift_zipkin() {
    return Codec.THRIFT.readSpan(localSpanThrift);
  }

  @Benchmark
  public zipkin.Span readLocalSpan_thrift_libthrift() {
    return toZipkinSpan(deserialize(localSpanThrift));
  }

  @Benchmark
  public byte[] writeLocalSpan_json_zipkin() {
    return Codec.JSON.writeSpan(localSpan);
  }

  @Benchmark
  public byte[] writeLocalSpan_thrift_zipkin() {
    return Codec.THRIFT.writeSpan(localSpan);
  }

  @Benchmark
  public byte[] writeLocalSpan_thrift_libthrift() throws TException {
    return serialize(localSpanLibThrift);
  }

  static final byte[] clientSpanJson = read("/span-client.json");
  static final zipkin.Span clientSpan = Codec.JSON.readSpan(clientSpanJson);
  static final byte[] clientSpanThrift = Codec.THRIFT.writeSpan(clientSpan);
  static final com.twitter.zipkin.thriftjava.Span clientSpanLibThrift =
      deserialize(clientSpanThrift);
  static final List<zipkin.Span> tenClientSpans = Collections.nCopies(10, clientSpan);
  static final byte[] tenClientSpansJson = Codec.JSON.writeSpans(tenClientSpans);
  static final byte[] tenClientSpansThrift = Codec.THRIFT.writeSpans(tenClientSpans);

  @Benchmark
  public zipkin.Span readClientSpan_json_zipkin() {
    return Codec.JSON.readSpan(clientSpanJson);
  }

  @Benchmark
  public List<zipkin.Span> readTenClientSpans_json_zipkin() {
    return Codec.JSON.readSpans(tenClientSpansJson);
  }

  @Benchmark
  public zipkin.Span readClientSpan_thrift_zipkin() {
    return Codec.THRIFT.readSpan(clientSpanThrift);
  }

  @Benchmark
  public List<zipkin.Span> readTenClientSpans_thrift_zipkin() {
    return Codec.THRIFT.readSpans(tenClientSpansThrift);
  }

  @Benchmark
  public zipkin.Span readClientSpan_thrift_libthrift() {
    return toZipkinSpan(deserialize(clientSpanThrift));
  }

  @Benchmark
  public byte[] writeClientSpan_json_zipkin() {
    return Codec.JSON.writeSpan(clientSpan);
  }

  @Benchmark
  public byte[] writeTenClientSpans_json_zipkin() {
    return Codec.JSON.writeSpans(tenClientSpans);
  }

  @Benchmark
  public byte[] writeClientSpan_thrift_zipkin() {
    return Codec.THRIFT.writeSpan(clientSpan);
  }

  @Benchmark
  public byte[] writeTenClientSpans_thrift_zipkin() {
    return Codec.THRIFT.writeSpans(tenClientSpans);
  }

  @Benchmark
  public byte[] writeClientSpan_thrift_libthrift() throws TException {
    return serialize(clientSpanLibThrift);
  }

  static final byte[] zipkin2Json = read("/zipkin2-client.json");
  static final Span zipkin2 = SpanBytesDecoder.JSON_V2.decodeOne(zipkin2Json);
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
  public Span readClientSpan_java_zipkin2() {
    return kryo.readObject(new Input(zipkin2Serialized), Span.class);
  }

  @Benchmark
  public byte[] writeClientSpan_java_zipkin2() {
    Output output = new Output(zipkin2Serialized.length);
    kryo.writeObject(output, zipkin2);
    output.flush();
    return output.getBuffer();
  }

  @Benchmark
  public Span readClientSpan_json_zipkin2() {
    return SpanBytesDecoder.JSON_V2.decodeOne(zipkin2Json);
  }

  @Benchmark
  public List<Span> readTenClientSpans_json_zipkin2() {
    return SpanBytesDecoder.JSON_V2.decodeList(tenSpan2sJson);
  }

  @Benchmark
  public byte[] writeClientSpan_json_zipkin2() {
    return SpanBytesEncoder.JSON_V2.encode(zipkin2);
  }

  @Benchmark
  public byte[] writeTenClientSpans_json_zipkin2() {
    return SpanBytesEncoder.JSON_V2.encodeList(tenSpan2s);
  }

  @Benchmark
  public byte[] writeClientSpan_json_zipkin2_legacy() {
    return SpanBytesEncoder.JSON_V1.encode(zipkin2);
  }

  @Benchmark
  public byte[] writeTenClientSpans_json_zipkin2_legacy() {
    return SpanBytesEncoder.JSON_V1.encodeList(tenSpan2s);
  }

  static final byte[] rpcSpanJson = read("/span-rpc.json");
  static final zipkin.Span rpcSpan = Codec.JSON.readSpan(rpcSpanJson);
  static final byte[] rpcSpanThrift = Codec.THRIFT.writeSpan(rpcSpan);
  static final com.twitter.zipkin.thriftjava.Span rpcSpanLibThrift = deserialize(rpcSpanThrift);

  @Benchmark
  public zipkin.Span readRpcSpan_json_zipkin() {
    return Codec.JSON.readSpan(rpcSpanJson);
  }

  @Benchmark
  public zipkin.Span readRpcSpan_thrift_zipkin() {
    return Codec.THRIFT.readSpan(rpcSpanThrift);
  }

  @Benchmark
  public zipkin.Span readRpcSpan_thrift_libthrift() {
    return toZipkinSpan(deserialize(rpcSpanThrift));
  }

  @Benchmark
  public byte[] writeRpcSpan_json_zipkin() {
    return Codec.JSON.writeSpan(rpcSpan);
  }

  @Benchmark
  public byte[] writeRpcSpan_thrift_zipkin() {
    return Codec.THRIFT.writeSpan(rpcSpan);
  }

  @Benchmark
  public byte[] writeRpcSpan_thrift_libthrift() throws TException {
    return serialize(rpcSpanLibThrift);
  }

  static final byte[] rpcV6SpanJson = read("/span-rpc-ipv6.json");
  static final zipkin.Span rpcV6Span = Codec.JSON.readSpan(rpcV6SpanJson);
  static final byte[] rpcV6SpanThrift = Codec.THRIFT.writeSpan(rpcV6Span);
  static final com.twitter.zipkin.thriftjava.Span rpcV6SpanLibThrift = deserialize(rpcV6SpanThrift);

  @Benchmark
  public zipkin.Span readRpcV6Span_json_zipkin() {
    return Codec.JSON.readSpan(rpcV6SpanJson);
  }

  @Benchmark
  public zipkin.Span readRpcV6Span_thrift_zipkin() {
    return Codec.THRIFT.readSpan(rpcV6SpanThrift);
  }

  @Benchmark
  public zipkin.Span readRpcV6Span_thrift_libthrift() {
    return toZipkinSpan(deserialize(rpcV6SpanThrift));
  }

  @Benchmark
  public byte[] writeRpcV6Span_json_zipkin() {
    return Codec.JSON.writeSpan(rpcV6Span);
  }

  @Benchmark
  public byte[] writeRpcV6Span_thrift_zipkin() {
    return Codec.THRIFT.writeSpan(rpcV6Span);
  }

  @Benchmark
  public byte[] writeRpcV6Span_thrift_libthrift() throws TException {
    return serialize(rpcV6SpanLibThrift);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + CodecBenchmarks.class.getSimpleName() + ".*kryo_zipkin2")
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

  private byte[] serialize(com.twitter.zipkin.thriftjava.Span thriftSpan) throws TException {
    // TSerializer isn't thread-safe
    return new TSerializer(TBINARY_PROTOCOL_FACTORY).serialize(thriftSpan);
  }

  private static com.twitter.zipkin.thriftjava.Span deserialize(byte[] serialized) {
    com.twitter.zipkin.thriftjava.Span result = new com.twitter.zipkin.thriftjava.Span();
    try {
      // TDeserializer isn't thread-safe
      new TDeserializer(TBINARY_PROTOCOL_FACTORY).deserialize(result, serialized);
    } catch (TException e) {
      throw new AssertionError(e);
    }
    return result;
  }

  /**
   * {@link zipkin.Span.Builder}, validates, doesn't return null for fields that aren't nullable,
   * uses immutable collections, etc. When comparing codec, make sure you copy-out as structs like
   * libthrift do no validation, which is cheaper, but not usable in zipkin.
   */
  private static zipkin.Span toZipkinSpan(com.twitter.zipkin.thriftjava.Span libthriftSpan) {
    zipkin.Span.Builder builder = zipkin.Span.builder()
        .traceId(libthriftSpan.trace_id)
        .id(libthriftSpan.id)
        .parentId(libthriftSpan.isSetParent_id() ? libthriftSpan.parent_id : null)
        .name(libthriftSpan.name)
        .timestamp(libthriftSpan.isSetTimestamp() ? libthriftSpan.timestamp : null)
        .duration(libthriftSpan.isSetDuration() ? libthriftSpan.duration : null)
        .debug(libthriftSpan.isSetDebug() ? libthriftSpan.debug : null);

    if (libthriftSpan.isSetAnnotations()) {
      for (Annotation a : libthriftSpan.annotations) {
        builder.addAnnotation(zipkin.Annotation.create(a.timestamp, a.value, a.isSetHost()
            ? Endpoint.builder()
            .serviceName(a.host.service_name)
            .ipv4(a.host.ipv4)
            .ipv6(a.host.getIpv6()).build()
            : null
        ));
      }
    }

    if (libthriftSpan.isSetBinary_annotations()) {
      for (BinaryAnnotation b : libthriftSpan.binary_annotations) {
        builder.addBinaryAnnotation(zipkin.BinaryAnnotation.create(b.key, b.getValue(),
            zipkin.BinaryAnnotation.Type.fromValue(b.getAnnotation_type().getValue()), b.isSetHost()
                ? Endpoint.builder()
                .serviceName(b.host.service_name)
                .ipv4(b.host.ipv4)
                .ipv6(b.host.getIpv6()).build()
                : null
        ));
      }
    }
    return builder.build();
  }
}
