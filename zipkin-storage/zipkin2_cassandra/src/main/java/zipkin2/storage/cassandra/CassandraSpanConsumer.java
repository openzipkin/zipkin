/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.utils.UUIDs;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.cassandra.DeduplicatingExecutor.BoundStatementKey;
import zipkin2.storage.cassandra.Schema.AnnotationUDT;
import zipkin2.storage.cassandra.Schema.EndpointUDT;

import static zipkin2.storage.cassandra.CassandraUtil.durationIndexBucket;

class CassandraSpanConsumer implements SpanConsumer { // not final for testing
  static final Joiner COMMA_JOINER = Joiner.on(',');

  private static final long WRITTEN_NAMES_TTL
      = Long.getLong("zipkin2.storage.cassandra.internal.writtenNamesTtl", 60 * 60 * 1000);

  private final Session session;
  private final boolean strictTraceId;
  private final PreparedStatement insertSpan;
  private final PreparedStatement insertTraceServiceSpanName;
  private final PreparedStatement insertServiceSpanName;
  private final DeduplicatingExecutor deduplicatingExecutor;

  CassandraSpanConsumer(CassandraStorage storage) {
    session = storage.session();
    strictTraceId = storage.strictTraceId();
    Schema.readMetadata(session);

    insertSpan = session.prepare(
        QueryBuilder
            .insertInto(Schema.TABLE_SPAN)
            .value("trace_id", QueryBuilder.bindMarker("trace_id"))
            .value("trace_id_high", QueryBuilder.bindMarker("trace_id_high"))
            .value("ts_uuid", QueryBuilder.bindMarker("ts_uuid"))
            .value("parent_id", QueryBuilder.bindMarker("parent_id"))
            .value("id", QueryBuilder.bindMarker("id"))
            .value("kind", QueryBuilder.bindMarker("kind"))
            .value("span", QueryBuilder.bindMarker("span"))
            .value("ts", QueryBuilder.bindMarker("ts"))
            .value("duration", QueryBuilder.bindMarker("duration"))
            .value("l_ep", QueryBuilder.bindMarker("l_ep"))
            .value("l_service", QueryBuilder.bindMarker("l_service"))
            .value("r_ep", QueryBuilder.bindMarker("r_ep"))
            .value("annotations", QueryBuilder.bindMarker("annotations"))
            .value("tags", QueryBuilder.bindMarker("tags"))
            .value("shared", QueryBuilder.bindMarker("shared"))
            .value("debug", QueryBuilder.bindMarker("debug"))
            .value("annotation_query", QueryBuilder.bindMarker("annotation_query")));

    insertTraceServiceSpanName = session.prepare(
        QueryBuilder
            .insertInto(Schema.TABLE_TRACE_BY_SERVICE_SPAN)
            .value("service", QueryBuilder.bindMarker("service"))
            .value("span", QueryBuilder.bindMarker("span"))
            .value("bucket", QueryBuilder.bindMarker("bucket"))
            .value("ts", QueryBuilder.bindMarker("ts"))
            .value("trace_id", QueryBuilder.bindMarker("trace_id"))
            .value("duration", QueryBuilder.bindMarker("duration")));

    insertServiceSpanName = session.prepare(
        QueryBuilder
            .insertInto(Schema.TABLE_SERVICE_SPANS)
            .value("service", QueryBuilder.bindMarker("service"))
            .value("span", QueryBuilder.bindMarker("span")));

    deduplicatingExecutor = new DeduplicatingExecutor(session, WRITTEN_NAMES_TTL);
  }

  /**
   * This fans out into many requests, last count was 2 * spans.size. If any of these fail, the
   * returned future will fail. Most callers drop or log the result.
   */
  @Override
  public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);

    List<BoundStatement> statements = new ArrayList<>();
    Set<BoundStatementKey> keys = new LinkedHashSet<>();
    for (Span s : spans) {
      // indexing occurs by timestamp, so derive one if not present.
      long ts_micro = s.timestamp() != null ? s.timestamp() : guessTimestamp(s);

      // fallback to current time on the ts_uuid for span data, so we know when it was inserted
      UUID ts_uuid = new UUID(
        UUIDs.startOf(ts_micro != 0L ? (ts_micro / 1000L) : System.currentTimeMillis())
          .getMostSignificantBits(),
        UUIDs.random().getLeastSignificantBits());

      statements.add(storeSpan(s, ts_uuid));

      // service span index is refreshed regardless of timestamp
      String span = null != s.name() ? s.name() : "";
      if (null != s.remoteServiceName()) { // allows getServices to return remote service names
        keys.add(storeServiceSpanName(s.remoteServiceName(), span));
      }

      String service = s.localServiceName();
      if (null == service) continue; // all of the following indexes require a local service name

      keys.add(storeServiceSpanName(service, span));

      if (ts_micro == 0L) continue; // search is only valid with a timestamp, don't index w/o it!

      // Contract for Repository.storeTraceServiceSpanName is to store the span twice, once with
      // the span name and another with empty string.
      statements.add(
        storeTraceServiceSpanName(s.traceId(), service, span, ts_micro, ts_uuid, s.duration())
      );
      if (span.isEmpty()) continue;
      statements.add( // Allows lookup without the span name
        storeTraceServiceSpanName(s.traceId(), service, "", ts_micro, ts_uuid, s.duration())
      );
    }
    return new StoreSpansCall(statements, keys);
  }

  /** Store the span in the underlying storage for later retrieval. */
  BoundStatement storeSpan(Span span, UUID ts_uuid) {
    boolean traceIdHigh = !strictTraceId && span.traceId().length() == 32;

    // start with the partition key
    BoundStatement bound = bindWithName(insertSpan, "insert-span")
      .setUUID("ts_uuid", ts_uuid)
      .setString("trace_id", traceIdHigh ? span.traceId().substring(16) : span.traceId())
      .setString("id", span.id());

    // now set the data fields
    if (traceIdHigh) {
      bound.setString("trace_id_high", span.traceId().substring(0, 16));
    }
    if (null != span.parentId()) {
      bound.setString("parent_id", span.parentId());
    }
    if (null != span.kind()) {
      bound.setString("kind", span.kind().name());
    }
    if (null != span.name()) {
      bound.setString("span", span.name());
    }
    if (null != span.timestamp()) {
      bound.setLong("ts", span.timestamp());
    }
    if (null != span.duration()) {
      bound.setLong("duration", span.duration());
    }
    if (null != span.localEndpoint()) {
      bound
        .set("l_ep", new EndpointUDT(span.localEndpoint()), EndpointUDT.class)
        .setString("l_service", span.localServiceName());
    }
    if (null != span.remoteEndpoint()) {
      bound.set("r_ep", new EndpointUDT(span.remoteEndpoint()), EndpointUDT.class);
    }
    if (!span.annotations().isEmpty()) {
      List<AnnotationUDT> annotations = span.annotations().stream()
        .map(a -> new AnnotationUDT(a))
        .collect(Collectors.toList());
      bound.setList("annotations", annotations);
    }
    if (!span.tags().isEmpty()) {
      bound.setMap("tags", span.tags());
    }
    Set<String> annotationQuery = CassandraUtil.annotationKeys(span);
    if (!annotationQuery.isEmpty()) {
      bound.setString("annotation_query", COMMA_JOINER.join(annotationQuery));
    }
    if (null != span.shared()) {
      bound.setBool("shared", span.shared());
    }
    if (null != span.debug()) {
      bound.setBool("debug", span.debug());
    }
    return bound;
  }

  BoundStatement storeTraceServiceSpanName(
      String traceId,
      String serviceName,
      String spanName,
      long ts_micro,
      UUID ts_uuid,
      Long duration) {

    int bucket = durationIndexBucket(ts_micro);
    BoundStatement bound =
        bindWithName(insertTraceServiceSpanName, "insert-trace-service-span-name")
            .setString("trace_id", traceId)
            .setString("service", serviceName)
            .setString("span", spanName)
            .setInt("bucket", bucket)
            .setUUID("ts", ts_uuid);

    if (null != duration) {
      // stored as milliseconds, not microseconds
      long durationMillis = TimeUnit.MICROSECONDS.toMillis(duration);
      bound.setLong("duration", durationMillis);
    }
    return bound;
  }

  BoundStatementKey storeServiceSpanName(String serviceName, String spanName) {
    BoundStatement bound = bindWithName(insertServiceSpanName, "insert-service-span-name")
      .setString("service", serviceName)
      .setString("span", spanName);

    return new BoundStatementKey(bound, serviceName + '෴' + spanName);
  }

  private static long guessTimestamp(Span span) {
    Preconditions.checkState(null == span.timestamp(), "method only for when span has no timestamp");
    for (Annotation annotation : span.annotations()) {
      if (0L < annotation.timestamp()) {
        return annotation.timestamp();
      }
    }
    return 0L; // return a timestamp that won't match a query
  }

  void clear() {
    deduplicatingExecutor.clear();
  }

  class StoreSpansCall extends ListenableFutureCall<Void> {
    final List<BoundStatement> statements;
    final Set<BoundStatementKey> serviceSpanKeys;

    StoreSpansCall(List<BoundStatement> statements, Set<BoundStatementKey> serviceSpanKeys) {
      this.statements = statements;
      this.serviceSpanKeys = serviceSpanKeys;
    }

    @Override protected ListenableFuture<Void> newFuture() {
      List<ListenableFuture<?>> futures = new ArrayList<>(statements.size());
      for (BoundStatement statement : statements) {
        futures.add(session.executeAsync(statement));
      }
      for (BoundStatementKey key : serviceSpanKeys) {
        futures.add(deduplicatingExecutor.maybeExecuteAsync(key));
      }
      return Futures.transform(Futures.allAsList(futures),
        (Function<List<Object>, Void>) input -> null);
    }

    @Override public StoreSpansCall clone() {
      return new StoreSpansCall(statements, serviceSpanKeys);
    }
  }

  BoundStatement bindWithName(PreparedStatement prepared, String name) { // overridable for tests
    return CassandraUtil.bindWithName(prepared, name);
  }
}
