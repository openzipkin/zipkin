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
package zipkin2.storage.cassandra;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.FilterTraces;
import zipkin2.internal.Nullable;
import zipkin2.storage.GroupByTraceId;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.StrictTraceId;
import zipkin2.storage.cassandra.internal.call.AccumulateAllResults;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static com.google.common.base.Preconditions.checkNotNull;
import static zipkin2.storage.cassandra.Schema.TABLE_SPAN;

final class SelectFromSpan extends ResultSetFutureCall<ResultSet> {

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final ReadSpans readSpans;
    final Call.Mapper<List<Span>, List<List<Span>>> groupByTraceId;
    final boolean strictTraceId;
    final int maxTraceCols;

    Factory(Session session, boolean strictTraceId, int maxTraceCols) {
      this.session = session;
      this.readSpans = new ReadSpans();
      this.preparedStatement =
          session.prepare(
              QueryBuilder.select(
                      "trace_id_high",
                      "trace_id",
                      "parent_id",
                      "id",
                      "kind",
                      "span",
                      "ts",
                      "duration",
                      "l_ep",
                      "r_ep",
                      "annotations",
                      "tags",
                      "shared",
                      "debug")
                  .from(TABLE_SPAN)
                  // when reading on the partition key, clustering keys are optional
                  .where(QueryBuilder.in("trace_id", QueryBuilder.bindMarker("trace_id")))
                  .limit(QueryBuilder.bindMarker("limit_")));
      this.strictTraceId = strictTraceId;
      this.maxTraceCols = maxTraceCols;
      this.groupByTraceId = GroupByTraceId.create(strictTraceId);
    }

    Call<List<Span>> newCall(String hexTraceId) {
      checkNotNull(hexTraceId, "hexTraceId");
      // Unless we are strict, truncate the trace ID to 64bit (encoded as 16 characters)
      Set<String> traceIds;
      if (!strictTraceId && hexTraceId.length() == 32) {
        traceIds = new LinkedHashSet<>();
        traceIds.add(hexTraceId);
        traceIds.add(hexTraceId.substring(16));
      } else {
        traceIds = Collections.singleton(hexTraceId);
      }

      Call<List<Span>> result = new SelectFromSpan(this, traceIds, maxTraceCols).flatMap(readSpans);
      return strictTraceId ? result.map(StrictTraceId.filterSpans(hexTraceId)) : result;
    }

    FlatMapper<Set<String>, List<List<Span>>> newFlatMapper(QueryRequest request) {
      return new SelectSpansByTraceIds(this, request);
    }
  }

  final Factory factory;
  final Set<String> trace_id;
  final int limit_;

  /** @param limit_ amount of spans per trace is almost always larger than trace IDs */
  SelectFromSpan(Factory factory, Set<String> trace_id, int limit_) {
    this.factory = factory;
    this.trace_id = trace_id;
    this.limit_ = limit_;
  }

  @Override
  protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(
        factory.preparedStatement.bind().setSet("trace_id", trace_id).setInt("limit_", limit_));
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override
  public String toString() {
    return "SelectFromSpan{trace_id=" + trace_id + ", limit_=" + limit_ + "}";
  }

  @Override
  public SelectFromSpan clone() {
    return new SelectFromSpan(factory, trace_id, limit_);
  }

  static final class SelectSpansByTraceIds implements FlatMapper<Set<String>, List<List<Span>>> {
    final Factory factory;
    final int limit;
    @Nullable final Call.Mapper<List<List<Span>>, List<List<Span>>> filter;

    SelectSpansByTraceIds(Factory factory, QueryRequest request) {
      this.factory = factory;
      this.limit = request.limit();
      // Cassandra always looks up traces by 64-bit trace ID, so we have to unconditionally filter
      // when strict trace ID is enabled.
      this.filter = factory.strictTraceId ? FilterTraces.create(request) : null;
    }

    @Override
    public Call<List<List<Span>>> map(Set<String> input) {
      if (input.isEmpty()) return Call.emptyList();
      Set<String> traceIds;
      if (input.size() > limit) {
        traceIds = new LinkedHashSet<>();
        Iterator<String> iterator = input.iterator();
        for (int i = 0; i < limit; i++) {
          traceIds.add(iterator.next());
        }
      } else {
        traceIds = input;
      }
      Call<List<List<Span>>> result =
          new SelectFromSpan(factory, traceIds, factory.maxTraceCols)
              .flatMap(factory.readSpans)
              .map(factory.groupByTraceId);
      return filter != null ? result.map(filter) : result;
    }

    @Override
    public String toString() {
      return "SelectSpansByTraceIds{limit=" + limit + "}";
    }
  }

  static final class ReadSpans extends AccumulateAllResults<List<Span>> {

    @Override
    protected Supplier<List<Span>> supplier() {
      return ArrayList::new;
    }

    @Override
    protected BiConsumer<Row, List<Span>> accumulator() {
      return (row, result) -> {
        String traceId = row.getString("trace_id");
        String traceIdHigh = row.getString("trace_id_high");
        if (traceIdHigh != null) traceId = traceIdHigh + traceId;
        Span.Builder builder =
            Span.newBuilder()
                .traceId(traceId)
                .parentId(row.getString("parent_id"))
                .id(row.getString("id"))
                .name(row.getString("span"))
                .timestamp(row.getLong("ts"));

        if (!row.isNull("duration")) {
          builder.duration(row.getLong("duration"));
        }
        if (!row.isNull("kind")) {
          try {
            builder.kind(Span.Kind.valueOf(row.getString("kind")));
          } catch (IllegalArgumentException ignored) {
          }
        }
        if (!row.isNull("l_ep")) {
          builder.localEndpoint(row.get("l_ep", Schema.EndpointUDT.class).toEndpoint());
        }
        if (!row.isNull("r_ep")) {
          builder.remoteEndpoint(row.get("r_ep", Schema.EndpointUDT.class).toEndpoint());
        }
        if (!row.isNull("shared")) {
          builder.shared(row.getBool("shared"));
        }
        if (!row.isNull("debug")) {
          builder.shared(row.getBool("debug"));
        }
        for (Schema.AnnotationUDT udt : row.getList("annotations", Schema.AnnotationUDT.class)) {
          builder.addAnnotation(udt.toAnnotation().timestamp(), udt.toAnnotation().value());
        }
        for (Entry<String, String> tag :
            row.getMap("tags", String.class, String.class).entrySet()) {
          builder.putTag(tag.getKey(), tag.getValue());
        }
        result.add(builder.build());
      };
    }

    @Override
    public String toString() {
      return "ReadSpans{}";
    }
  }
}
