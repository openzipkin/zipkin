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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import zipkin2.Call;
import zipkin2.internal.Nullable;
import zipkin2.storage.cassandra.Schema.AnnotationUDT;
import zipkin2.storage.cassandra.Schema.EndpointUDT;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_SPAN;

final class InsertSpan extends ResultSetFutureCall {
  @AutoValue static abstract class Input {
    abstract UUID ts_uuid();

    @Nullable abstract String trace_id_high();

    abstract String trace_id();

    @Nullable abstract String parent_id();

    abstract String id();

    @Nullable abstract String kind();

    @Nullable abstract String span();

    abstract long ts();

    abstract long duration();

    @Nullable abstract EndpointUDT l_ep();

    @Nullable abstract EndpointUDT r_ep();

    abstract List<AnnotationUDT> annotations();

    abstract Map<String, String> tags();

    @Nullable abstract String annotation_query();

    abstract boolean shared();

    abstract boolean debug();
  }

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final boolean strictTraceId, searchEnabled;

    Factory(Session session, boolean strictTraceId, boolean searchEnabled) {
      this.session = session;
      Insert insertQuery = QueryBuilder.insertInto(TABLE_SPAN)
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
        .value("r_ep", QueryBuilder.bindMarker("r_ep"))
        .value("annotations", QueryBuilder.bindMarker("annotations"))
        .value("tags", QueryBuilder.bindMarker("tags"))
        .value("shared", QueryBuilder.bindMarker("shared"))
        .value("debug", QueryBuilder.bindMarker("debug"));

      if (searchEnabled) {
        insertQuery.value("l_service", QueryBuilder.bindMarker("l_service"));
        insertQuery.value("annotation_query", QueryBuilder.bindMarker("annotation_query"));
      }

      this.preparedStatement = session.prepare(insertQuery);
      this.strictTraceId = strictTraceId;
      this.searchEnabled = searchEnabled;
    }

    Input newInput(zipkin2.Span span, UUID ts_uuid) {
      boolean traceIdHigh = !strictTraceId && span.traceId().length() == 32;
      List<AnnotationUDT> annotations;
      if (!span.annotations().isEmpty()) {
        annotations = span.annotations().stream()
          .map(AnnotationUDT::new)
          .collect(Collectors.toList());
      } else {
        annotations = Collections.emptyList();
      }
      String annotation_query = searchEnabled ? CassandraUtil.annotationQuery(span): null;
      return new AutoValue_InsertSpan_Input(
        ts_uuid,
        traceIdHigh ? span.traceId().substring(0, 16) : null,
        traceIdHigh ? span.traceId().substring(16) : span.traceId(),
        span.parentId(),
        span.id(),
        span.kind() != null ? span.kind().name() : null,
        span.name(),
        span.timestampAsLong(),
        span.durationAsLong(),
        span.localEndpoint() != null ? new EndpointUDT(span.localEndpoint()) : null,
        span.remoteEndpoint() != null ? new EndpointUDT(span.remoteEndpoint()) : null,
        annotations,
        span.tags(),
        annotation_query,
        Boolean.TRUE.equals(span.debug()),
        Boolean.TRUE.equals(span.shared())
      );
    }

    Call<ResultSet> create(Input span) {
      return new InsertSpan(this, span);
    }
  }

  final Factory factory;
  final Input input;

  InsertSpan(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  /**
   * TLDR: we are guarding against setting null, as doing so implies tombstones. We are dodging setX
   * to keep code simpler than other alternatives described below.
   *
   * <p>If there's consistently 8 tombstones (nulls) per row, then we'll only need 125 spans in a
   * trace (rows in a partition) to trigger the `tombstone_warn_threshold warnings being logged in
   * the C* nodes. And if we go to 12500 spans in a trace then that whole trace partition would
   * become unreadable. Cassandra warns at a 1000 tombstones in any query, and fails on 100000
   * tombstones.
   *
   * <p>There's also a small question about disk usage efficiency. Each tombstone is a cell name and
   * basically empty cell value entry stored on disk. Given that the cells are, apart from tags and
   * annotations, generally very small then this could be proportionally an unnecessary waste of
   * disk.
   *
   * <p>To avoid this relying upon a number of variant prepared statements for inserting a span is
   * the normal practice.
   *
   * <p>Another popular practice is to insert those potentially null columns as separate statements
   * (and optionally put them together into UNLOGGED batches). This works as multiple writes to the
   * same partition has little overhead, and here we're not worried about lack of isolation between
   * those writes, as the write is asynchronous anyway. An example of this approach is in the
   * cassandra-reaper project here: https://github.com/thelastpickle/cassandra-reaper/blob/master/src/server/src/main/java/io/cassandrareaper/storage/CassandraStorage.java#L622-L642
   */
  @Override protected ResultSetFuture newFuture() {
    BoundStatement bound = factory.preparedStatement.bind()
      .setUUID("ts_uuid", input.ts_uuid())
      .setString("trace_id", input.trace_id())
      .setString("id", input.id());

    // now set the nullable fields
    if (null != input.trace_id_high()) bound.setString("trace_id_high", input.trace_id_high());
    if (null != input.parent_id()) bound.setString("parent_id", input.parent_id());
    if (null != input.kind()) bound.setString("kind", input.kind());
    if (null != input.span()) bound.setString("span", input.span());
    if (0L != input.ts()) bound.setLong("ts", input.ts());
    if (0L != input.duration()) bound.setLong("duration", input.duration());
    if (null != input.l_ep()) bound.set("l_ep", input.l_ep(), EndpointUDT.class);
    if (null != input.r_ep()) bound.set("r_ep", input.r_ep(), EndpointUDT.class);
    if (!input.annotations().isEmpty()) bound.setList("annotations", input.annotations());
    if (!input.tags().isEmpty()) bound.setMap("tags", input.tags());
    if (input.shared()) bound.setBool("shared", true);
    if (input.debug()) bound.setBool("debug", true);

    if (factory.searchEnabled) {
      if (null != input.l_ep()) bound.setString("l_service", input.l_ep().getService());
      if (null != input.annotation_query()) {
        bound.setString("annotation_query", input.annotation_query());
      }
    }
    return factory.session.executeAsync(bound);
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertSpan");
  }

  @Override public InsertSpan clone() {
    return new InsertSpan(factory, input);
  }
}
