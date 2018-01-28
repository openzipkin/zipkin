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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.cassandra.internal.call.AccumulateAllResults;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static com.google.common.base.Preconditions.checkNotNull;
import static zipkin2.storage.cassandra.Schema.TABLE_SPAN;

final class SelectFromSpan extends ResultSetFutureCall {

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final AccumulateSpansAllResults accumulateSpans;
    final Call.Mapper<List<Span>, List<List<Span>>> groupByTraceId;
    final boolean strictTraceId;
    final int maxTraceCols;

    Factory(Session session, boolean strictTraceId, int maxTraceCols) {
      this.session = session;
      this.accumulateSpans = new AccumulateSpansAllResults(strictTraceId);
      this.preparedStatement = session.prepare(QueryBuilder.select(
        "trace_id_high", "trace_id", "parent_id", "id", "kind", "span", "ts",
        "duration", "l_ep", "r_ep", "annotations", "tags", "shared", "debug")
        .from(TABLE_SPAN)
        // when reading on the partition key, clustering keys are optional
        .where(QueryBuilder.in("trace_id", QueryBuilder.bindMarker("trace_id")))
        .limit(QueryBuilder.bindMarker("limit_"))
      );
      this.strictTraceId = strictTraceId;
      this.maxTraceCols = maxTraceCols;
      this.groupByTraceId = new GroupByTraceId(strictTraceId);
    }

    Call<List<Span>> newCall(String traceId) {
      checkNotNull(traceId, "traceId");
      // Unless we are strict, truncate the trace ID to 64bit (encoded as 16 characters)
      Set<String> traceIds;
      if (!strictTraceId && traceId.length() == 32) {
        traceIds = new LinkedHashSet<>();
        traceIds.add(traceId);
        traceIds.add(traceId.substring(16));
      } else {
        traceIds = Collections.singleton(traceId);
      }

      return new SelectFromSpan(this,
        traceIds,
        maxTraceCols // amount of spans per trace is almost always larger than trace IDs
      ).flatMap(accumulateSpans);
    }

    FlatMapper<Set<String>, List<List<Span>>> newFlatMapper(int limit) {
      return new FlatMapTraceIdsToSelectFromSpans(limit);
    }

    class FlatMapTraceIdsToSelectFromSpans implements FlatMapper<Set<String>, List<List<Span>>> {
      final int limit;

      FlatMapTraceIdsToSelectFromSpans(int limit) {
        this.limit = limit;
      }

      @Override public String toString() {
        return "FlatMapTraceIdsToSelectFromSpans{limit=" + limit + "}";
      }

      @Override public Call<List<List<Span>>> map(Set<String> input) {
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
        return new SelectFromSpan(Factory.this,
          traceIds,
          maxTraceCols // amount of spans per trace is almost always larger than trace IDs
        ).flatMap(accumulateSpans).map(groupByTraceId);
      }
    }
  }

  final Factory factory;
  final Set<String> trace_id;
  final int limit_;

  SelectFromSpan(Factory factory, Set<String> trace_id, int limit_) {
    this.factory = factory;
    this.trace_id = trace_id;
    this.limit_ = limit_;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setSet("trace_id", trace_id)
      .setInt("limit_", limit_));
  }

  @Override public String toString() {
    return "SelectFromSpan{trace_id=" + trace_id + ", limit_=" + limit_ + "}";
  }

  @Override public SelectFromSpan clone() {
    return new SelectFromSpan(factory, trace_id, limit_);
  }

  static class AccumulateSpansAllResults extends AccumulateAllResults<List<Span>> {
    final boolean strictTraceId;

    AccumulateSpansAllResults(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
    }

    @Override protected Supplier<List<Span>> supplier() {
      return ArrayList::new;
    }

    @Override protected BiConsumer<Row, List<Span>> accumulator() {
      return (row, result) -> {
        String traceId = row.getString("trace_id");
        if (!strictTraceId) {
          String traceIdHigh = row.getString("trace_id_high");
          if (traceIdHigh != null) traceId = traceIdHigh + traceId;
        }
        Span.Builder builder = Span.newBuilder()
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
          builder = builder.localEndpoint(row.get("l_ep", Schema.EndpointUDT.class).toEndpoint());
        }
        if (!row.isNull("r_ep")) {
          builder = builder.remoteEndpoint(row.get("r_ep", Schema.EndpointUDT.class).toEndpoint());
        }
        if (!row.isNull("shared")) {
          builder = builder.shared(row.getBool("shared"));
        }
        if (!row.isNull("debug")) {
          builder = builder.shared(row.getBool("debug"));
        }
        for (Schema.AnnotationUDT udt : row.getList("annotations", Schema.AnnotationUDT.class)) {
          builder =
            builder.addAnnotation(udt.toAnnotation().timestamp(), udt.toAnnotation().value());
        }
        for (Entry<String, String> tag : row.getMap("tags", String.class, String.class)
          .entrySet()) {
          builder = builder.putTag(tag.getKey(), tag.getValue());
        }
        result.add(builder.build());
      };
    }

    @Override public String toString() {
      return "AccumulateSpansAllResults{}";
    }
  }

  // TODO(adriancole): at some later point we can refactor this out
  static class GroupByTraceId implements Call.Mapper<List<Span>, List<List<Span>>> {
    final boolean strictTraceId;

    GroupByTraceId(boolean strictTraceId) {
      this.strictTraceId = strictTraceId;
    }

    @Override public List<List<Span>> map(List<Span> input) {
      if (input.isEmpty()) return Collections.emptyList();

      Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<>();
      for (Span span : input) {
        String traceId = strictTraceId || span.traceId().length() == 16
          ? span.traceId()
          : span.traceId().substring(16);
        if (!groupedByTraceId.containsKey(traceId)) {
          groupedByTraceId.put(traceId, new ArrayList<>());
        }
        groupedByTraceId.get(traceId).add(span);
      }
      return new ArrayList<>(groupedByTraceId.values());
    }

    @Override public String toString() {
      return "GroupByTraceId{strictTraceId=" + strictTraceId + "}";
    }
  }
}
