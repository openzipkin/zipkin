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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.V1ThriftSpanWriter;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.cassandra.internal.call.AggregateCall;
import zipkin2.v1.V1Span;
import zipkin2.v1.V2SpanConverter;

final class CassandraSpanConsumer implements SpanConsumer {
  static final int WRITTEN_NAMES_TTL =
      Integer.getInteger("zipkin.store.cassandra.internal.writtenNamesTtl", 60 * 60 * 1000);

  final InsertTrace.Factory insertTrace;
  final InsertServiceName.Factory insertServiceName;
  final InsertSpanName.Factory insertSpanName;
  final Schema.Metadata metadata;
  final CompositeIndexer indexer;

  CassandraSpanConsumer(CassandraStorage storage, CacheBuilderSpec indexCacheSpec) {
    Session session = storage.session.get();
    metadata = Schema.readMetadata(session);
    int indexTtl = metadata.hasDefaultTtl ? 0 : storage.indexTtl;
    int spanTtl = metadata.hasDefaultTtl ? 0 : storage.spanTtl;
    insertTrace = new InsertTrace.Factory(session, metadata, spanTtl);
    insertServiceName = new InsertServiceName.Factory(session, indexTtl, WRITTEN_NAMES_TTL);
    insertSpanName = new InsertSpanName.Factory(session, indexTtl, WRITTEN_NAMES_TTL);
    indexer = new CompositeIndexer(session, indexCacheSpec, storage.bucketCount, indexTtl);
  }

  /**
   * This fans out into many requests, last count was 8 * spans.size. If any of these fail, the
   * returned future will fail. Most callers drop or log the result.
   */
  @Override
  public Call<Void> accept(List<Span> rawSpans) {
    ImmutableList.Builder<V1Span> spansToIndex = ImmutableList.builder();

    V2SpanConverter converter = V2SpanConverter.create();
    V1ThriftSpanWriter encoder = new V1ThriftSpanWriter();

    Set<InsertTrace.Input> insertTraces = new LinkedHashSet<>();
    Set<String> insertServiceNames = new LinkedHashSet<>();
    Set<InsertSpanName.Input> insertSpanNames = new LinkedHashSet<>();

    for (Span v2 : rawSpans) {
      V1Span span = converter.convert(v2);
      // indexing occurs by timestamp, so derive one if not present.
      long ts_micro = v2.timestampAsLong();
      if (ts_micro == 0L) ts_micro = guessTimestamp(v2);

      insertTraces.add(insertTrace.newInput(span, encoder.write(v2), ts_micro));

      for (String serviceName : span.serviceNames()) {
        insertServiceNames.add(serviceName);
        if (span.name() == null) continue;
        insertSpanNames.add(insertSpanName.newInput(serviceName, span.name()));
      }

      if (ts_micro == 0L) continue; // search is only valid with a timestamp, don't index w/o it!
      spansToIndex.add(span);
    }

    List<Call<ResultSet>> calls = new ArrayList<>();
    for (InsertTrace.Input insert : insertTraces) {
      calls.add(insertTrace.create(insert));
    }
    for (String insert : insertServiceNames) {
      calls.add(insertServiceName.create(insert));
    }
    for (InsertSpanName.Input insert : insertSpanNames) {
      calls.add(insertSpanName.create(insert));
    }

    indexer.index(spansToIndex.build(), calls);
    return new StoreSpansCall(calls);
  }

  /** Clears any caches */
  @VisibleForTesting
  void clear() {
    insertServiceName.cache.clear();
    insertSpanName.cache.clear();
    indexer.clear();
  }

  private static long guessTimestamp(Span span) {
    assert 0L == span.timestampAsLong() : "method only for when span has no timestamp";
    for (Annotation annotation : span.annotations()) {
      if (0L < annotation.timestamp()) return annotation.timestamp();
    }
    return 0L; // return a timestamp that won't match a query
  }

  static final class StoreSpansCall extends AggregateCall<ResultSet, Void> {
    StoreSpansCall(List<Call<ResultSet>> calls) {
      super(calls);
    }

    volatile boolean empty = true;

    @Override
    protected Void newOutput() {
      return null;
    }

    @Override
    protected void append(ResultSet input, Void output) {
      empty = false;
    }

    @Override
    protected boolean isEmpty(Void output) {
      return empty;
    }

    @Override
    public StoreSpansCall clone() {
      return new StoreSpansCall(cloneCalls());
    }
  }
}
