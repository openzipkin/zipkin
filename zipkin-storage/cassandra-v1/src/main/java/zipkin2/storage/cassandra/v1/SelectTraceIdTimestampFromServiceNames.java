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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

/**
 * Just like {@link SelectTraceIdTimestampFromServiceName} except provides an IN server-side query.
 *
 * <p>Note: this is only supported in Cassandra 2.2+
 */
final class SelectTraceIdTimestampFromServiceNames extends ResultSetFutureCall<ResultSet> {
  @AutoValue abstract static class Input {
    static Input create(List<String> service_names, long start_ts, long end_ts, int limit_) {
      return new AutoValue_SelectTraceIdTimestampFromServiceNames_Input(
        service_names, start_ts, end_ts, limit_);
    }

    abstract List<String> service_names();

    abstract long start_ts();

    abstract long end_ts();

    abstract int limit_();
  }

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final TimestampCodec timestampCodec;

    Factory(Session session, TimestampCodec timestampCodec, Set<Integer> buckets) {
      this.session = session;
      this.timestampCodec = timestampCodec;
      this.preparedStatement =
        session.prepare(
          QueryBuilder.select("ts", "trace_id")
            .from(Tables.SERVICE_NAME_INDEX)
            .where(QueryBuilder.in("service_name", QueryBuilder.bindMarker("service_names")))
            .and(QueryBuilder.in("bucket", buckets))
            .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
            .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
            .limit(QueryBuilder.bindMarker("limit_"))
            .orderBy(QueryBuilder.desc("ts")));
    }

    Call<Set<Pair>> newCall(Input input) {
      return new SelectTraceIdTimestampFromServiceNames(this, input)
        .flatMap(new AccumulateTraceIdTsLong(timestampCodec));
    }

    FlatMapper<List<String>, Set<Pair>> newFlatMapper(long endTs, long lookback, int limit) {
      return new FlatMapServiceNamesToInput(endTs, lookback, limit);
    }

    class FlatMapServiceNamesToInput implements FlatMapper<List<String>, Set<Pair>> {
      final Input input;

      FlatMapServiceNamesToInput(long endTs, long lookback, int limit) {
        long startTs = Math.max(endTs - lookback, 0); // >= 1970
        this.input = Input.create(Collections.emptyList(), startTs, endTs, limit);
      }

      @Override public Call<Set<Pair>> map(List<String> serviceNames) {
        return newCall(
          Input.create(serviceNames, input.start_ts(), input.end_ts(), input.limit_())
        );
      }

      @Override public String toString() {
        return "FlatMapServiceNamesToInput{" +
          input.toString().replace("Input", "SelectTraceIdTimestampFromServiceNames") + "}";
      }
    }
  }

  final Factory factory;
  final Input input;

  SelectTraceIdTimestampFromServiceNames(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    Statement bound = factory.preparedStatement.bind()
      .setList("service_names", input.service_names())
      .setBytesUnsafe("start_ts", factory.timestampCodec.serialize(input.start_ts()))
      .setBytesUnsafe("end_ts", factory.timestampCodec.serialize(input.end_ts()))
      .setInt("limit_", input.limit_())
      .setFetchSize(Integer.MAX_VALUE); // NOTE in the new driver, we also set this to limit
    return factory.session.executeAsync(bound);
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override public String toString() {
    return input.toString().replace("Input", "SelectTraceIdTimestampFromServiceNames");
  }

  @Override public SelectTraceIdTimestampFromServiceNames clone() {
    return new SelectTraceIdTimestampFromServiceNames(factory, input);
  }
}
