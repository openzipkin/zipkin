/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal;

import com.linecorp.armeria.common.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
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
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.BulkCallBuilder.IndexEntry;

import static java.nio.charset.StandardCharsets.UTF_8;
import static zipkin2.elasticsearch.ElasticsearchVersion.V6_0;
import static zipkin2.storage.cassandra.internal.Resources.resourceToString;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(2)
public class BulkRequestBenchmarks {
  static final Span CLIENT_SPAN =
    SpanBytesDecoder.JSON_V2.decodeOne(resourceToString("/zipkin2-client.json").getBytes(UTF_8));

  final ElasticsearchStorage es = ElasticsearchStorage.newBuilder(() -> null).build();
  final long indexTimestamp = CLIENT_SPAN.timestampAsLong() / 1000L;
  final String spanIndex =
    es.indexNameFormatter().formatTypeAndTimestampForInsert("span", '-', indexTimestamp);
  final IndexEntry<Span> entry =
    BulkCallBuilder.newIndexEntry(spanIndex, "span", CLIENT_SPAN, BulkIndexWriter.SPAN);

  @Benchmark public ByteBuf writeRequest_singleSpan() {
    return BulkCallBuilder.serialize(PooledByteBufAllocator.DEFAULT, entry, true);
  }

  @Benchmark public HttpRequest buildAndWriteRequest_singleSpan() {
    BulkCallBuilder builder = new BulkCallBuilder(es, V6_0, "index-span");
    builder.index(spanIndex, "span", CLIENT_SPAN, BulkIndexWriter.SPAN);
    return builder.build().request.get();
  }

  @Benchmark public HttpRequest buildAndWriteRequest_tenSpans() {
    BulkCallBuilder builder = new BulkCallBuilder(es, V6_0, "index-span");
    for (int i = 0; i < 10; i++) {
      builder.index(spanIndex, "span", CLIENT_SPAN, BulkIndexWriter.SPAN);
    }
    return builder.build().request.get();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + BulkRequestBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
