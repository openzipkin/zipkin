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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.utils.UUIDs;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.AggregateCall;
import zipkin2.internal.Nullable;
import zipkin2.storage.SpanConsumer;

import static zipkin2.storage.cassandra.CassandraUtil.durationIndexBucket;

class CassandraSpanConsumer implements SpanConsumer { // not final for testing
  final Session session;
  final boolean strictTraceId, searchEnabled;
  final InsertSpan.Factory insertSpan;
  @Nullable final InsertTraceByServiceRemoteService.Factory insertTraceByServiceRemoteService;
  @Nullable final InsertTraceByServiceSpan.Factory insertTraceByServiceSpan;
  @Nullable final InsertServiceSpan.Factory insertServiceSpan;
  @Nullable final InsertServiceRemoteService.Factory insertServiceRemoteService;
  @Nullable final InsertAutocompleteValue.Factory insertAutocompleteValue;
  final Set<String> autocompleteKeys;

  CassandraSpanConsumer(CassandraStorage storage) {
    session = storage.session();
    Schema.Metadata metadata = storage.metadata();
    strictTraceId = storage.strictTraceId();
    searchEnabled = storage.searchEnabled();
    autocompleteKeys = new LinkedHashSet<>(storage.autocompleteKeys());

    insertSpan = new InsertSpan.Factory(session, strictTraceId, searchEnabled);
    if (searchEnabled) {
      insertTraceByServiceSpan = new InsertTraceByServiceSpan.Factory(session, strictTraceId);
      if (metadata.hasRemoteService) {
        insertTraceByServiceRemoteService =
          new InsertTraceByServiceRemoteService.Factory(session, strictTraceId);
        insertServiceRemoteService = new InsertServiceRemoteService.Factory(storage);
      } else {
        insertTraceByServiceRemoteService = null;
        insertServiceRemoteService = null;
      }
      insertServiceSpan = new InsertServiceSpan.Factory(storage);
      if (metadata.hasAutocompleteTags && !storage.autocompleteKeys().isEmpty()) {
        insertAutocompleteValue = new InsertAutocompleteValue.Factory(storage);
      } else {
        insertAutocompleteValue = null;
      }
    } else {
      insertTraceByServiceRemoteService = null;
      insertTraceByServiceSpan = null;
      insertServiceRemoteService = null;
      insertServiceSpan = null;
      insertAutocompleteValue = null;
    }
  }

  /**
   * This fans out into many requests, last count was 2 * spans.size. If any of these fail, the
   * returned future will fail. Most callers drop or log the result.
   */
  @Override public Call<Void> accept(List<Span> input) {
    if (input.isEmpty()) return Call.create(null);

    Set<InsertSpan.Input> spans = new LinkedHashSet<>();
    Set<InsertServiceRemoteService.Input> serviceRemoteServices = new LinkedHashSet<>();
    Set<InsertServiceSpan.Input> serviceSpans = new LinkedHashSet<>();
    Set<InsertTraceByServiceRemoteService.Input> traceByServiceRemoteServices =
      new LinkedHashSet<>();
    Set<InsertTraceByServiceSpan.Input> traceByServiceSpans = new LinkedHashSet<>();
    Set<Map.Entry<String, String>> autocompleteTags = new LinkedHashSet<>();

    for (Span s : input) {
      // indexing occurs by timestamp, so derive one if not present.
      long ts_micro = s.timestampAsLong();
      if (ts_micro == 0L) ts_micro = guessTimestamp(s);

      // fallback to current time on the ts_uuid for span data, so we know when it was inserted
      UUID ts_uuid =
        new UUID(
          UUIDs.startOf(ts_micro != 0L ? (ts_micro / 1000L) : System.currentTimeMillis())
            .getMostSignificantBits(),
          UUIDs.random().getLeastSignificantBits());

      spans.add(insertSpan.newInput(s, ts_uuid));

      if (!searchEnabled) continue;

      // Empty values allow for api queries with blank service or span name
      String service = s.localServiceName() != null ? s.localServiceName() : "";
      String span =
        null != s.name() ? s.name() : ""; // Empty value allows for api queries without span name

      if (null == s.localServiceName()) continue; // don't index further w/o a service name

      // service span and remote service indexes is refreshed regardless of timestamp
      String remoteService = s.remoteServiceName();
      if (insertServiceRemoteService != null && remoteService != null) {
        serviceRemoteServices.add(insertServiceRemoteService.newInput(service, remoteService));
      }
      serviceSpans.add(insertServiceSpan.newInput(service, span));

      if (ts_micro == 0L) continue; // search is only valid with a timestamp, don't index w/o it!
      int bucket = durationIndexBucket(ts_micro); // duration index is milliseconds not microseconds
      long duration = s.durationAsLong() / 1000L;
      traceByServiceSpans.add(
        insertTraceByServiceSpan.newInput(service, span, bucket, ts_uuid, s.traceId(), duration));
      if (span.isEmpty()) continue;

      if (insertServiceRemoteService != null && remoteService != null) {
        traceByServiceRemoteServices.add(
          insertTraceByServiceRemoteService.newInput(service, remoteService, bucket, ts_uuid,
            s.traceId()));
      }
      traceByServiceSpans.add( // Allows lookup without the span name
        insertTraceByServiceSpan.newInput(service, "", bucket, ts_uuid, s.traceId(), duration));

      if (insertAutocompleteValue != null) {
        for (Map.Entry<String, String> entry : s.tags().entrySet()) {
          if (autocompleteKeys.contains(entry.getKey())) autocompleteTags.add(entry);
        }
      }
    }
    List<Call<Void>> calls = new ArrayList<>();
    for (InsertSpan.Input span : spans) {
      calls.add(insertSpan.create(span));
    }
    if (searchEnabled) {
      for (InsertServiceSpan.Input serviceSpan : serviceSpans) {
        insertServiceSpan.maybeAdd(serviceSpan, calls);
      }
      for (InsertServiceRemoteService.Input serviceRemoteService : serviceRemoteServices) {
        insertServiceRemoteService.maybeAdd(serviceRemoteService, calls);
      }
      for (InsertTraceByServiceSpan.Input serviceSpan : traceByServiceSpans) {
        calls.add(insertTraceByServiceSpan.create(serviceSpan));
      }
      for (InsertTraceByServiceRemoteService.Input serviceRemoteService : traceByServiceRemoteServices) {
        calls.add(insertTraceByServiceRemoteService.create(serviceRemoteService));
      }
    }
    for (Map.Entry<String, String> autocompleteTag : autocompleteTags) {
      insertAutocompleteValue.maybeAdd(autocompleteTag, calls);
    }
    return AggregateCall.newVoidCall(calls);
  }

  static long guessTimestamp(Span span) {
    assert 0L == span.timestampAsLong() : "method only for when span has no timestamp";
    for (Annotation annotation : span.annotations()) {
      if (0L < annotation.timestamp()) {
        return annotation.timestamp();
      }
    }
    return 0L; // return a timestamp that won't match a query
  }
}
