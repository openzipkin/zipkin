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
import java.util.Set;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static com.google.common.base.Preconditions.checkArgument;

final class SelectTraceIdTimestampFromServiceSpanName extends ResultSetFutureCall<ResultSet> {
  @AutoValue
  abstract static class Input {
    abstract String service_span_name();

    abstract long start_ts();

    abstract long end_ts();

    abstract int limit_();
  }

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final TimestampCodec timestampCodec;

    Factory(Session session, TimestampCodec timestampCodec) {
      this.session = session;
      this.timestampCodec = timestampCodec;
      this.preparedStatement =
          session.prepare(
              QueryBuilder.select("ts", "trace_id")
                  .from(Tables.SERVICE_SPAN_NAME_INDEX)
                  .where(
                      QueryBuilder.eq(
                          "service_span_name", QueryBuilder.bindMarker("service_span_name")))
                  .and(QueryBuilder.gte("ts", QueryBuilder.bindMarker("start_ts")))
                  .and(QueryBuilder.lte("ts", QueryBuilder.bindMarker("end_ts")))
                  .limit(QueryBuilder.bindMarker("limit_"))
                  .orderBy(QueryBuilder.desc("ts")));
    }

    Call<Set<Pair>> newCall(
        String serviceName, String spanName, long endTs, long lookback, int limit) {
      checkArgument(serviceName != null, "serviceName required on spanName query");
      checkArgument(spanName != null, "spanName required on spanName query");
      String serviceSpanName = serviceName + "." + spanName;
      long startTs = Math.max(endTs - lookback, 0); // >= 1970
      Input input =
          new AutoValue_SelectTraceIdTimestampFromServiceSpanName_Input(
              serviceSpanName, startTs, endTs, limit);

      return new SelectTraceIdTimestampFromServiceSpanName(this, input)
          .flatMap(new AccumulateTraceIdTsLong(timestampCodec));
    }
  }

  final Factory factory;
  final Input input;

  SelectTraceIdTimestampFromServiceSpanName(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override
  protected ResultSetFuture newFuture() {
    Statement bound =
        factory
            .preparedStatement
            .bind()
            .setString("service_span_name", input.service_span_name())
            .setBytesUnsafe("start_ts", factory.timestampCodec.serialize(input.start_ts()))
            .setBytesUnsafe("end_ts", factory.timestampCodec.serialize(input.end_ts()))
            .setInt("limit_", input.limit_())
            .setFetchSize(Integer.MAX_VALUE); // NOTE in the new driver, we also set this to limit
    return factory.session.executeAsync(bound);
  }

  @Override public ResultSet map(ResultSet input) {
    return input;
  }

  @Override
  public String toString() {
    return input.toString().replace("Input", "SelectTraceIdTimestampFromServiceName");
  }

  @Override
  public SelectTraceIdTimestampFromServiceSpanName clone() {
    return new SelectTraceIdTimestampFromServiceSpanName(factory, input);
  }
}
