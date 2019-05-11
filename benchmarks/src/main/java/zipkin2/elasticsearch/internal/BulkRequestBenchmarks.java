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
package zipkin2.elasticsearch.internal;

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okio.Okio;
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
import zipkin2.codec.CodecBenchmarks;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.BulkCallBuilder.IndexEntry;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(2)
public class BulkRequestBenchmarks {
  static final Span CLIENT_SPAN = SpanBytesDecoder.JSON_V2.decodeOne(read("/zipkin2-client.json"));

  final ElasticsearchStorage es = ElasticsearchStorage.newBuilder().build();
  final long indexTimestamp = CLIENT_SPAN.timestampAsLong() / 1000L;
  final String spanIndex =
    es.indexNameFormatter().formatTypeAndTimestampForInsert("span", '-', indexTimestamp);
  final IndexEntry<Span> entry =
    BulkCallBuilder.newIndexEntry(spanIndex, "span", CLIENT_SPAN, BulkIndexWriter.SPAN);

  @Benchmark public void writeRequest_singleSpan() throws IOException {
    BulkCallBuilder.write(Okio.buffer(Okio.blackhole()), entry, true);
  }

  @Benchmark public void buildAndWriteRequest_singleSpan() throws IOException {
    BulkCallBuilder builder = new BulkCallBuilder(es, 6.7f, "index-span");
    builder.index(spanIndex, "span", CLIENT_SPAN, BulkIndexWriter.SPAN);
    builder.build().call.request().body().writeTo(Okio.buffer(Okio.blackhole()));
  }

  @Benchmark public void buildAndWriteRequest_tenSpans() throws IOException {
    BulkCallBuilder builder = new BulkCallBuilder(es, 6.7f, "index-span");
    for (int i = 0; i < 10; i++) {
      builder.index(spanIndex, "span", CLIENT_SPAN, BulkIndexWriter.SPAN);
    }
    builder.build().call.request().body().writeTo(Okio.buffer(Okio.blackhole()));
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + BulkRequestBenchmarks.class.getSimpleName() + ".*")
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
