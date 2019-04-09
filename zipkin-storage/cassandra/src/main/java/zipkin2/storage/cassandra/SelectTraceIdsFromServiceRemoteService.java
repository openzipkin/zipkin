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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import zipkin2.Call;
import zipkin2.storage.cassandra.CassandraSpanStore.TimestampRange;
import zipkin2.storage.cassandra.internal.call.AccumulateTraceIdTsUuid;
import zipkin2.storage.cassandra.internal.call.AggregateIntoSet;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE;

final class SelectTraceIdsFromServiceRemoteService extends ResultSetFutureCall<ResultSet> {
  @AutoValue abstract static class Input {
    abstract String service();

    abstract String remote_service();

    abstract int bucket();

    abstract UUID start_ts();

    abstract UUID end_ts();

    abstract int limit_();

    Input withService(String service) {
      return new AutoValue_SelectTraceIdsFromServiceRemoteService_Input(
        service,
        remote_service(),
        bucket(),
        start_ts(),
        end_ts(),
        limit_());
    }
  }

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;

    Factory(Session session) {
      this.session = session;
      this.preparedStatement = session.prepare(QueryBuilder.select("ts", "trace_id")
        .from(TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE)
        .where(QueryBuilder.eq("service", QueryBuilder.bindMarker("service")))
        .and(QueryBuilder.eq("remote_service", QueryBuilder.bindMarker("remote_service")))
        .and(QueryBuilder.eq("bucket", QueryBuilder.bindMarker("bucket")))
        .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
        .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
        .limit(QueryBuilder.bindMarker("limit_")));
    }

    Input newInput(
      String serviceName,
      String remoteServiceName,
      int bucket,
      TimestampRange timestampRange,
      int limit) {
      return new AutoValue_SelectTraceIdsFromServiceRemoteService_Input(
        serviceName,
        remoteServiceName,
        bucket,
        timestampRange.startUUID,
        timestampRange.endUUID,
        limit);
    }

    Call<Set<Entry<String, Long>>> newCall(List<Input> inputs) {
      if (inputs.isEmpty()) return Call.create(Collections.emptySet());
      if (inputs.size() == 1) return newCall(inputs.get(0));

      List<Call<Set<Entry<String, Long>>>> bucketedTraceIdCalls = new ArrayList<>();
      for (SelectTraceIdsFromServiceRemoteService.Input input : inputs) {
        bucketedTraceIdCalls.add(newCall(input));
      }
      return new AggregateIntoSet<>(bucketedTraceIdCalls);
    }

    Call<Set<Entry<String, Long>>> newCall(Input input) {
      return new SelectTraceIdsFromServiceRemoteService(this, preparedStatement, input)
        .flatMap(new AccumulateTraceIdTsUuid());
    }

    /** Applies all deferred service names to all input templates */
    FlatMapper<List<String>, Set<Entry<String, Long>>> newFlatMapper(List<Input> inputTemplates) {
      return new FlatMapServicesToInputs(inputTemplates);
    }

    class FlatMapServicesToInputs implements FlatMapper<List<String>, Set<Entry<String, Long>>> {
      final List<Input> inputTemplates;

      FlatMapServicesToInputs(List<Input> inputTemplates) {
        this.inputTemplates = inputTemplates;
      }

      @Override public Call<Set<Entry<String, Long>>> map(List<String> serviceNames) {
        List<Call<Set<Entry<String, Long>>>> bucketedTraceIdCalls = new ArrayList<>();

        for (String service : serviceNames) { // fan out every input for each service name
          List<Input> scopedInputs = new ArrayList<>();
          for (Input input : inputTemplates) {
            scopedInputs.add(input.withService(service));
          }
          bucketedTraceIdCalls.add(newCall(scopedInputs));
        }

        if (bucketedTraceIdCalls.isEmpty()) return Call.create(Collections.emptySet());
        if (bucketedTraceIdCalls.size() == 1) return bucketedTraceIdCalls.get(0);
        return new AggregateIntoSet<>(bucketedTraceIdCalls);
      }

      @Override public String toString() {
        return "FlatMapServicesToInputs{" + inputTemplates + "}";
      }
    }
  }

  final Factory factory;
  final PreparedStatement preparedStatement;
  final Input input;

  SelectTraceIdsFromServiceRemoteService(Factory factory, PreparedStatement preparedStatement,
    Input input) {
    this.factory = factory;
    this.preparedStatement = preparedStatement;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    Statement bound = preparedStatement.bind()
      .setString("service", input.service())
      .setString("remote_service", input.remote_service())
      .setInt("bucket", input.bucket())
      .setUUID("start_ts", input.start_ts())
      .setUUID("end_ts", input.end_ts())
      .setInt("limit_", input.limit_())
      .setFetchSize(input.limit_());
    return factory.session.executeAsync(bound);
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override public String toString() {
    return input.toString().replace("Input", "SelectTraceIdsFromServiceRemoteService");
  }

  @Override public SelectTraceIdsFromServiceRemoteService clone() {
    return new SelectTraceIdsFromServiceRemoteService(factory, preparedStatement, input);
  }
}
