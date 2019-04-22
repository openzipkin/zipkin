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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilderSpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.AggregateCall;
import zipkin2.internal.Nullable;
import zipkin2.internal.V1ThriftSpanWriter;
import zipkin2.storage.SpanConsumer;
import zipkin2.v1.V1Span;
import zipkin2.v1.V2SpanConverter;

final class CassandraSpanConsumer implements SpanConsumer {
  final InsertTrace.Factory insertTrace;
  final boolean searchEnabled;
  final Set<String> autocompleteKeys;

  // Everything below here is null when search is disabled
  @Nullable final InsertServiceName.Factory insertServiceName;
  @Nullable final InsertRemoteServiceName.Factory insertRemoteServiceName;
  @Nullable final InsertSpanName.Factory insertSpanName;
  @Nullable final CompositeIndexer indexer;
  @Nullable final InsertAutocompleteValue.Factory insertAutocompleteValue;

  CassandraSpanConsumer(CassandraStorage storage, CacheBuilderSpec indexCacheSpec) {
    Session session = storage.session();
    Schema.Metadata metadata = storage.metadata();
    searchEnabled = storage.searchEnabled;
    autocompleteKeys = new LinkedHashSet<>(storage.autocompleteKeys);
    int spanTtl = metadata.hasDefaultTtl ? 0 : storage.spanTtl;

    insertTrace = new InsertTrace.Factory(session, metadata, spanTtl);

    if (!searchEnabled) {
      insertServiceName = null;
      insertRemoteServiceName = null;
      insertSpanName = null;
      indexer = null;
      insertAutocompleteValue = null;
      return;
    }

    int indexTtl = metadata.hasDefaultTtl ? 0 : storage.indexTtl;
    insertServiceName = new InsertServiceName.Factory(storage, indexTtl);
    if (metadata.hasRemoteService) {
      insertRemoteServiceName = new InsertRemoteServiceName.Factory(storage, indexTtl);
    } else {
      insertRemoteServiceName = null;
    }
    insertSpanName = new InsertSpanName.Factory(storage, indexTtl);
    indexer = new CompositeIndexer(storage, indexCacheSpec, indexTtl);
    if (metadata.hasAutocompleteTags && !storage.autocompleteKeys.isEmpty()) {
      insertAutocompleteValue = new InsertAutocompleteValue.Factory(storage, indexTtl);
    } else {
      insertAutocompleteValue = null;
    }
  }

  /**
   * This fans out into many requests, last count was 8 * spans.size. If any of these fail, the
   * returned future will fail. Most callers drop or log the result.
   */
  @Override
  public Call<Void> accept(List<Span> rawSpans) {
    V2SpanConverter converter = V2SpanConverter.create();
    V1ThriftSpanWriter encoder = new V1ThriftSpanWriter();

    Set<InsertTrace.Input> insertTraces = new LinkedHashSet<>();
    Set<String> insertServiceNames = new LinkedHashSet<>();
    Set<InsertRemoteServiceName.Input> insertRemoteServiceNames = new LinkedHashSet<>();
    Set<InsertSpanName.Input> insertSpanNames = new LinkedHashSet<>();
    Set<Map.Entry<String, String>> autocompleteTags = new LinkedHashSet<>();

    List<Call<Void>> calls = new ArrayList<>();
    for (Span v2 : rawSpans) {
      V1Span span = converter.convert(v2);
      // indexing occurs by timestamp, so derive one if not present.
      long ts_micro = v2.timestampAsLong();
      if (ts_micro == 0L) ts_micro = guessTimestamp(v2);

      insertTraces.add(insertTrace.newInput(span, encoder.write(v2), ts_micro));

      if (!searchEnabled) continue;

      if (insertAutocompleteValue != null) {
        for (Map.Entry<String, String> entry : v2.tags().entrySet()) {
          if (autocompleteKeys.contains(entry.getKey())) autocompleteTags.add(entry);
        }
      }

      // service span and remote service indexes is refreshed regardless of timestamp
      String serviceName = v2.localServiceName();
      if (serviceName != null) {
        insertServiceNames.add(serviceName);
        if (v2.name() != null) insertSpanNames.add(insertSpanName.newInput(serviceName, v2.name()));
        if (insertRemoteServiceName != null && v2.remoteServiceName() != null) {
          insertRemoteServiceNames.add(
            insertRemoteServiceName.newInput(serviceName, v2.remoteServiceName()));
        }
      }

      if (ts_micro == 0L) continue; // search is only valid with a timestamp, don't index w/o it!
      indexer.index(v2, calls);
    }

    for (InsertTrace.Input insert : insertTraces) {
      calls.add(insertTrace.create(insert));
    }
    for (String insert : insertServiceNames) {
      insertServiceName.maybeAdd(insert, calls);
    }
    for (InsertRemoteServiceName.Input insert : insertRemoteServiceNames) {
      insertRemoteServiceName.maybeAdd(insert, calls);
    }
    for (InsertSpanName.Input insert : insertSpanNames) {
      insertSpanName.maybeAdd(insert, calls);
    }
    for (Map.Entry<String, String> autocompleteTag : autocompleteTags) {
      insertAutocompleteValue.maybeAdd(autocompleteTag, calls);
    }
    return calls.isEmpty() ? Call.create(null) : AggregateCall.newVoidCall(calls);
  }

  /** Clears any caches */
  @VisibleForTesting
  void clear() {
    insertServiceName.clear();
    if (insertRemoteServiceName != null) insertRemoteServiceName.clear();
    insertSpanName.clear();
    indexer.clear();
    if (insertAutocompleteValue != null) insertAutocompleteValue.clear();
  }

  private static long guessTimestamp(Span span) {
    assert 0L == span.timestampAsLong() : "method only for when span has no timestamp";
    for (Annotation annotation : span.annotations()) {
      if (0L < annotation.timestamp()) return annotation.timestamp();
    }
    return 0L; // return a timestamp that won't match a query
  }
}
