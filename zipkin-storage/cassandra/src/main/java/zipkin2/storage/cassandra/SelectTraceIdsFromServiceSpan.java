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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import zipkin2.Call;
import zipkin2.internal.Nullable;
import zipkin2.storage.cassandra.CassandraSpanStore.TimestampRange;
import zipkin2.storage.cassandra.internal.call.AccumulateTraceIdTsUuid;
import zipkin2.storage.cassandra.internal.call.AggregateIntoSet;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_SPAN;

final class SelectTraceIdsFromServiceSpan extends ResultSetFutureCall<ResultSet> {
  @AutoValue
  abstract static class Input {
    abstract String service();

    abstract String span();

    abstract int bucket();

    @Nullable
    abstract Long start_duration();

    @Nullable
    abstract Long end_duration();

    abstract UUID start_ts();

    abstract UUID end_ts();

    abstract int limit_();

    Input withService(String service) {
      return new AutoValue_SelectTraceIdsFromServiceSpan_Input(
          service,
          span(),
          bucket(),
          start_duration(),
          end_duration(),
          start_ts(),
          end_ts(),
          limit_());
    }
  }

  static class Factory {
    final Session session;
    final PreparedStatement selectTraceIdsByServiceSpanName;
    final PreparedStatement selectTraceIdsByServiceSpanNameAndDuration;

    Factory(Session session) {
      this.session = session;
      // separate to avoid: "Unsupported unset value for column duration" maybe SASI related
      // TODO: revisit on next driver update
      this.selectTraceIdsByServiceSpanName =
          session.prepare(
              QueryBuilder.select("ts", "trace_id")
                  .from(TABLE_TRACE_BY_SERVICE_SPAN)
                  .where(QueryBuilder.eq("service", QueryBuilder.bindMarker("service")))
                  .and(QueryBuilder.eq("span", QueryBuilder.bindMarker("span")))
                  .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
                  .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
                  .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
                  .limit(QueryBuilder.bindMarker("limit_")));
      this.selectTraceIdsByServiceSpanNameAndDuration =
          session.prepare(
              QueryBuilder.select("ts", "trace_id")
                  .from(TABLE_TRACE_BY_SERVICE_SPAN)
                  .where(QueryBuilder.eq("service", QueryBuilder.bindMarker("service")))
                  .and(QueryBuilder.eq("span", QueryBuilder.bindMarker("span")))
                  .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
                  .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
                  .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
                  .and(QueryBuilder.gte("duration", QueryBuilder.bindMarker("start_duration")))
                  .and(QueryBuilder.lte("duration", QueryBuilder.bindMarker("end_duration")))
                  .limit(QueryBuilder.bindMarker("limit_")));
    }

    Input newInput(
        String serviceName,
        String spanName,
        int bucket,
        @Nullable Long minDurationMicros,
        @Nullable Long maxDurationMicros,
        TimestampRange timestampRange,
        int limit) {
      Long start_duration = null, end_duration = null;
      if (minDurationMicros != null) {
        start_duration = minDurationMicros / 1000L;
        end_duration = maxDurationMicros != null ? maxDurationMicros / 1000L : Long.MAX_VALUE;
      }
      return new AutoValue_SelectTraceIdsFromServiceSpan_Input(
          serviceName,
          spanName,
          bucket,
          start_duration,
          end_duration,
          timestampRange.startUUID,
          timestampRange.endUUID,
          limit);
    }

    Call<Set<Entry<String, Long>>> newCall(List<Input> inputs) {
      if (inputs.isEmpty()) return Call.create(Collections.emptySet());
      if (inputs.size() == 1) return newCall(inputs.get(0));

      List<Call<Set<Entry<String, Long>>>> bucketedTraceIdCalls = new ArrayList<>();
      for (SelectTraceIdsFromServiceSpan.Input input : inputs) {
        bucketedTraceIdCalls.add(newCall(input));
      }
      return new AggregateIntoSet<>(bucketedTraceIdCalls);
    }

    Call<Set<Entry<String, Long>>> newCall(Input input) {
      return new SelectTraceIdsFromServiceSpan(
              this,
              input.start_duration() != null
                  ? selectTraceIdsByServiceSpanNameAndDuration
                  : selectTraceIdsByServiceSpanName,
              input)
          .flatMap(new AccumulateTraceIdTsUuid());
    }

    /** Applies all deferred service names to all input templates */
    FlatMapper<List<String>, Set<Entry<String, Long>>> newFlatMapper(List<Input> inputTemplates) {
      return new FlatMapServicesToInputs(inputTemplates);
    }

    class FlatMapServicesToInputs implements FlatMapper<List<String>, Set<Entry<String, Long>>> {
      final List<SelectTraceIdsFromServiceSpan.Input> inputTemplates;

      FlatMapServicesToInputs(List<SelectTraceIdsFromServiceSpan.Input> inputTemplates) {
        this.inputTemplates = inputTemplates;
      }

      @Override
      public Call<Set<Entry<String, Long>>> map(List<String> serviceNames) {
        List<Call<Set<Entry<String, Long>>>> bucketedTraceIdCalls = new ArrayList<>();

        for (String service : serviceNames) { // fan out every input for each service name
          List<SelectTraceIdsFromServiceSpan.Input> scopedInputs = new ArrayList<>();
          for (SelectTraceIdsFromServiceSpan.Input input : inputTemplates) {
            scopedInputs.add(input.withService(service));
          }
          bucketedTraceIdCalls.add(newCall(scopedInputs));
        }

        if (bucketedTraceIdCalls.isEmpty()) return Call.create(Collections.emptySet());
        if (bucketedTraceIdCalls.size() == 1) return bucketedTraceIdCalls.get(0);
        return new AggregateIntoSet<>(bucketedTraceIdCalls);
      }

      @Override
      public String toString() {
        return "FlatMapServicesToInputs{" + inputTemplates + "}";
      }
    }
  }

  final Factory factory;
  final PreparedStatement preparedStatement;
  final Input input;

  SelectTraceIdsFromServiceSpan(Factory factory, PreparedStatement preparedStatement, Input input) {
    this.factory = factory;
    this.preparedStatement = preparedStatement;
    this.input = input;
  }

  @Override
  protected ResultSetFuture newFuture() {
    BoundStatement bound =
        preparedStatement
            .bind()
            .setString("service", input.service())
            .setString("span", input.span())
            .setInt("bucket", input.bucket());
    if (input.start_duration() != null) {
      bound.setLong("start_duration", input.start_duration());
      bound.setLong("end_duration", input.end_duration());
    }
    bound
        .setUUID("start_ts", input.start_ts())
        .setUUID("end_ts", input.end_ts())
        .setInt("limit_", input.limit_())
        .setFetchSize(input.limit_());
    return factory.session.executeAsync(bound);
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override
  public String toString() {
    return input.toString().replace("Input", "SelectTraceIdsFromServiceSpan");
  }

  @Override
  public SelectTraceIdsFromServiceSpan clone() {
    return new SelectTraceIdsFromServiceSpan(factory, preparedStatement, input);
  }
}
