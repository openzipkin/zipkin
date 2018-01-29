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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.utils.UUIDs;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.Nullable;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.cassandra.internal.call.AggregateCall;

import static zipkin2.storage.cassandra.CassandraUtil.durationIndexBucket;

class CassandraSpanConsumer implements SpanConsumer { // not final for testing
  private static final long WRITTEN_NAMES_TTL
    = Long.getLong("zipkin2.storage.cassandra.internal.writtenNamesTtl", 60 * 60 * 1000);

  private final Session session;
  private final boolean strictTraceId, searchEnabled;
  private final InsertSpan.Factory insertSpan;
  @Nullable final InsertTraceByServiceSpan.Factory insertTraceByServiceSpan;
  @Nullable private final InsertServiceSpan.Factory insertServiceSpanName;

  CassandraSpanConsumer(CassandraStorage storage) {
    session = storage.session();
    strictTraceId = storage.strictTraceId();
    searchEnabled = storage.searchEnabled();

    // warns when schema problems exist
    Schema.readMetadata(session);

    insertSpan = new InsertSpan.Factory(session, strictTraceId, searchEnabled);
    if (searchEnabled) {
      insertTraceByServiceSpan = new InsertTraceByServiceSpan.Factory(session, strictTraceId);
      insertServiceSpanName = new InsertServiceSpan.Factory(session, WRITTEN_NAMES_TTL);
    } else {
      insertTraceByServiceSpan = null;
      insertServiceSpanName = null;
    }
  }

  /**
   * This fans out into many requests, last count was 2 * spans.size. If any of these fail, the
   * returned future will fail. Most callers drop or log the result.
   */
  @Override
  public Call<Void> accept(List<Span> input) {
    if (input.isEmpty()) return Call.create(null);

    Set<InsertSpan.Input> spans = new LinkedHashSet<>();
    Set<InsertServiceSpan.Input> serviceSpans = new LinkedHashSet<>();
    Set<InsertTraceByServiceSpan.Input> traceByServiceSpans = new LinkedHashSet<>();

    for (Span s : input) {
      // indexing occurs by timestamp, so derive one if not present.
      long ts_micro = s.timestampAsLong();
      if (ts_micro == 0L) ts_micro = guessTimestamp(s);

      // fallback to current time on the ts_uuid for span data, so we know when it was inserted
      UUID ts_uuid = new UUID(
        UUIDs.startOf(ts_micro != 0L ? (ts_micro / 1000L) : System.currentTimeMillis())
          .getMostSignificantBits(),
        UUIDs.random().getLeastSignificantBits());

      spans.add(insertSpan.newInput(s, ts_uuid));

      if (!searchEnabled) continue;

      // Empty values allow for api queries with blank service or span name
      String service = s.localServiceName() != null ? s.localServiceName() : "";
      String span =
        null != s.name() ? s.name() : "";  // Empty value allows for api queries without span name

      // service span index is refreshed regardless of timestamp
      if (null != s.remoteServiceName()) { // allows getServices to return remote service names
        // TODO: this is busy-work as there's no query by remote service name!
        serviceSpans.add(insertServiceSpanName.newInput(s.remoteServiceName(), span));
      }
      if (null == s.localServiceName()) continue; // don't index further w/o a service name

      serviceSpans.add(insertServiceSpanName.newInput(service, span));

      if (ts_micro == 0L) continue; // search is only valid with a timestamp, don't index w/o it!
      int bucket = durationIndexBucket(ts_micro); // duration index is milliseconds not microseconds
      long duration = s.durationAsLong() / 1000L;
      traceByServiceSpans.add(
        insertTraceByServiceSpan.newInput(service, span, bucket, ts_uuid, s.traceId(), duration)
      );
      if (span.isEmpty()) continue;
      traceByServiceSpans.add( // Allows lookup without the span name
        insertTraceByServiceSpan.newInput(service, "", bucket, ts_uuid, s.traceId(), duration)
      );
    }
    List<Call<ResultSet>> calls = new ArrayList<>();
    for (InsertSpan.Input span : spans) {
      calls.add(insertSpan.create(span));
    }
    if (searchEnabled) {
      for (InsertServiceSpan.Input serviceSpan : serviceSpans) {
        calls.add(insertServiceSpanName.create(serviceSpan));
      }
      for (InsertTraceByServiceSpan.Input serviceSpan : traceByServiceSpans) {
        calls.add(insertTraceByServiceSpan.create(serviceSpan));
      }
    }
    return new StoreSpansCall(calls);
  }

  private static long guessTimestamp(Span span) {
    Preconditions.checkState(0L == span.timestampAsLong(),
      "method only for when span has no timestamp");
    for (Annotation annotation : span.annotations()) {
      if (0L < annotation.timestamp()) {
        return annotation.timestamp();
      }
    }
    return 0L; // return a timestamp that won't match a query
  }

  static final class StoreSpansCall extends AggregateCall<ResultSet, Void> {
    StoreSpansCall(List<Call<ResultSet>> calls) {
      super(calls);
    }

    volatile boolean empty = true;

    @Override protected Void newOutput() {
      return null;
    }

    @Override protected void append(ResultSet input, Void output) {
      empty = false;
    }

    @Override protected boolean isEmpty(Void output) {
      return empty;
    }

    @Override public StoreSpansCall clone() {
      return new StoreSpansCall(cloneCalls());
    }
  }
}
