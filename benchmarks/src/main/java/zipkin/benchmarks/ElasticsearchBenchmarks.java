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
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.bytes.ChannelBufferBytesReference;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
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

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static zipkin.internal.Util.UTF_8;

/**
 * Elasticsearch indexing is via {@link TransportClient}. Json is written into a command via {@link
 * StreamOutput#writeBytesReference(BytesReference)}. This tests the overhead of inserting a field
 * for the timestamp in milliseconds. It compares this against the base-case (not adding a field),
 * and by using arrays instead of channel buffers.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class ElasticsearchBenchmarks {
  static final byte[] TIMESTAMP_MILLIS_PREFIX = "{\"timestamp_millis\":".getBytes(UTF_8);
  static final long TIMESTAMP_MILLIS = 146175049127L;
  static final byte[] tinySpan = read("/span-local.json");
  // client+server spans aren't "normal" because a collector usually only processes spans reported
  // from a single host.
  static final byte[] normalSpan = read("/span-client.json");

  @Benchmark
  public int writeTo_tinySpan() throws IOException {
    return writeBytesReference_byteArray(tinySpan);
  }

  @Benchmark
  public int writeTo_tinySpan_prefixingTimestampMillis_withChannelBuffer() throws IOException {
    ChannelBuffer spanBytes = prefix_channelBuffer(tinySpan);
    return writeBytesReference_channelBuffer(spanBytes);
  }

  @Benchmark
  public int writeTo_tinySpan_prefixingTimestampMillis_withByteArray() throws IOException {
    byte[] spanBytes = prefix_byteArray(tinySpan);
    return writeBytesReference_byteArray(spanBytes);
  }

  @Benchmark
  public int writeTo_normalSpan() throws IOException {
    return writeBytesReference_byteArray(normalSpan);
  }

  @Benchmark
  public int writeTo_normalSpan_prefixingTimestampMillis_withChannelBuffer() throws IOException {
    ChannelBuffer spanBytes = prefix_channelBuffer(normalSpan);
    return writeBytesReference_channelBuffer(spanBytes);
  }

  @Benchmark
  public int writeTo_normalSpan_prefixingTimestampMillis_withByteArray() throws IOException {
    byte[] spanBytes = prefix_byteArray(normalSpan);
    return writeBytesReference_byteArray(spanBytes);
  }

  static ChannelBuffer prefix_channelBuffer(byte[] input) {
    ChannelBuffer timestampMillisPrefix = wrappedBuffer(TIMESTAMP_MILLIS_PREFIX);
    String dateAsString = Long.toString(TIMESTAMP_MILLIS);
    ChannelBuffer dateComma = ChannelBuffers.buffer(dateAsString.length() + 1 /* comma*/);
    for (int i = 0, length = dateAsString.length(); i < length; i++) {
      dateComma.writeByte((byte) dateAsString.charAt(i));
    }
    dateComma.writeByte(',');
    ChannelBuffer json = wrappedBuffer(input);
    json.readByte(); // discard the old head of '{'
    return wrappedBuffer(timestampMillisPrefix, dateComma, json);
  }

  static byte[] prefix_byteArray(byte[] json) {
    String dateAsString = Long.toString(TIMESTAMP_MILLIS);
    byte[] newSpanBytes =
        new byte[TIMESTAMP_MILLIS_PREFIX.length + dateAsString.length() + json.length];
    int pos = 0;
    System.arraycopy(TIMESTAMP_MILLIS_PREFIX, 0, newSpanBytes, pos, TIMESTAMP_MILLIS_PREFIX.length);
    pos += TIMESTAMP_MILLIS_PREFIX.length;
    for (int i = 0, length = dateAsString.length(); i < length; i++) {
      newSpanBytes[pos++] = (byte) dateAsString.charAt(i);
    }
    newSpanBytes[pos++] = ',';
    // starting at position 1 discards the old head of '{' 
    System.arraycopy(json, 1, newSpanBytes, pos, json.length - 1);
    return newSpanBytes;
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    if (!new String(prefix_byteArray("{\"count\":1}".getBytes(UTF_8)), UTF_8)
        .equals("{\"timestamp_millis\":" + Long.toString(TIMESTAMP_MILLIS) + ",\"count\":1}")) {
      throw new IllegalStateException("buggy code");
    }
    if (!new String(prefix_channelBuffer("{\"count\":1}".getBytes(UTF_8)).toByteBuffer().array(), UTF_8)
        .equals("{\"timestamp_millis\":" + Long.toString(TIMESTAMP_MILLIS) + ",\"count\":1}")) {
      throw new IllegalStateException("buggy code");
    }
    Options opt = new OptionsBuilder()
        .include(".*" + ElasticsearchBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }

  static byte[] read(String resource) {
    try {
      return ByteStreams.toByteArray(ElasticsearchBenchmarks.class.getResourceAsStream(resource));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static int writeBytesReference_byteArray(byte[] spanBytes) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    out.writeBytesReference(new BytesArray(spanBytes));
    return out.hashCode();
  }

  static int writeBytesReference_channelBuffer(ChannelBuffer spanBytes) throws IOException {
    BytesStreamOutput out = new BytesStreamOutput();
    out.writeBytesReference(new ChannelBufferBytesReference(spanBytes));
    return out.hashCode();
  }
}
