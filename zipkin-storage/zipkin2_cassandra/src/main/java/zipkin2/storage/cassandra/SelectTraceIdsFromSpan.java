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
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.google.auto.value.AutoValue;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import zipkin2.Call;
import zipkin2.internal.Nullable;
import zipkin2.storage.cassandra.CassandraSpanStore.TimestampRange;
import zipkin2.storage.cassandra.internal.call.AccumulateAllResults;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static zipkin2.storage.cassandra.Schema.TABLE_SPAN;

/**
 * Selects from the {@link Schema#TABLE_SPAN} using data in the partition key or SASI indexes.
 *
 * <p>Note: While queries here use {@link Select#allowFiltering()}, they do so within a SASI clause,
 * and only return (traceId, timestamp) tuples. This means the entire spans table is not scanned,
 * unless the time range implies that.
 *
 * <p>The spans table is sorted descending by timestamp.  When a query includes only a time range,
 * the first N rows are already in the correct order. However, the cardinality of rows is a function
 * of span count, not trace count. This implies an over-fetch function based on average span count
 * per trace in order to achieve N distinct trace IDs. For example if there are 3 spans per trace,
 * and over-fetch function of 3 * intended limit will work. See {@link
 * CassandraStorage#indexFetchMultiplier()} for an associated parameter.
 */
final class SelectTraceIdsFromSpan extends ResultSetFutureCall {
  @AutoValue static abstract class Input {
    @Nullable abstract String l_service();

    @Nullable abstract String annotation_query();

    abstract UUID start_ts();

    abstract UUID end_ts();

    abstract int limit_();
  }

  static class Factory {
    final Session session;
    final PreparedStatement withAnnotationQuery, withServiceAndAnnotationQuery;

    Factory(Session session) {
      this.session = session;
      // separate to avoid: "Unsupported unset value for column duration" maybe SASI related
      // TODO: revisit on next driver update
      this.withAnnotationQuery = session.prepare(
        QueryBuilder.select("ts", "trace_id").from(TABLE_SPAN)
          .where(QueryBuilder.like("annotation_query", bindMarker("annotation_query")))
          .and(QueryBuilder.gte("ts_uuid", bindMarker("start_ts")))
          .and(QueryBuilder.lte("ts_uuid", bindMarker("end_ts")))
          .limit(bindMarker("limit_"))
          .allowFiltering());
      this.withServiceAndAnnotationQuery = session.prepare(
        QueryBuilder.select("ts", "trace_id").from(TABLE_SPAN)
          .where(QueryBuilder.eq("l_service", bindMarker("l_service")))
          .and(QueryBuilder.like("annotation_query", bindMarker("annotation_query")))
          .and(QueryBuilder.gte("ts_uuid", bindMarker("start_ts")))
          .and(QueryBuilder.lte("ts_uuid", bindMarker("end_ts")))
          .limit(bindMarker("limit_"))
          .allowFiltering());
    }

    Call<Set<Entry<String, Long>>> newCall(
      @Nullable String serviceName,
      String annotationKey,
      TimestampRange timestampRange,
      int limit
    ) {
      Input input = new AutoValue_SelectTraceIdsFromSpan_Input(
        serviceName,
        // % for like, bracing with ░ to ensure no accidental substring match
        "%░" + annotationKey + "░%",
        timestampRange.startUUID,
        timestampRange.endUUID,
        limit
      );
      return new SelectTraceIdsFromSpan(
        this,
        serviceName != null ? withServiceAndAnnotationQuery : withAnnotationQuery,
        input
      ).flatMap(new AccumulateTraceIdTsLong());
    }
  }

  final Factory factory;
  final PreparedStatement preparedStatement;
  final Input input;

  SelectTraceIdsFromSpan(Factory factory, PreparedStatement preparedStatement, Input input) {
    this.factory = factory;
    this.preparedStatement = preparedStatement;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    BoundStatement bound = preparedStatement.bind();
    if (input.l_service() != null) bound.setString("l_service", input.l_service());
    if (input.annotation_query() != null) {
      bound.setString("annotation_query", input.annotation_query());
    }
    bound.setUUID("start_ts", input.start_ts())
      .setUUID("end_ts", input.end_ts())
      .setInt("limit_", input.limit_())
      .setFetchSize(input.limit_());
    return factory.session.executeAsync(bound);
  }

  @Override public SelectTraceIdsFromSpan clone() {
    return new SelectTraceIdsFromSpan(factory, preparedStatement, input);
  }

  @Override public String toString() {
    return input.toString().replace("Input", "SelectTraceIdsFromSpan");
  }

  static final class AccumulateTraceIdTsLong
    extends AccumulateAllResults<Set<Entry<String, Long>>> {

    @Override protected Supplier<Set<Entry<String, Long>>> supplier() {
      return LinkedHashSet::new; // because results are not distinct
    }

    @Override protected BiConsumer<Row, Set<Entry<String, Long>>> accumulator() {
      return (row, result) -> {
        if (row.isNull("ts")) return;
        result.add(new AbstractMap.SimpleEntry<>(
          row.getString("trace_id"), row.getLong("ts")
        ));
      };
    }

    @Override public String toString() {
      return "AccumulateTraceIdTsLong{}";
    }
  }
}
