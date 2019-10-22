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
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import java.util.UUID;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE;

final class InsertTraceByServiceRemoteService extends ResultSetFutureCall<Void> {

  @AutoValue abstract static class Input {
    abstract String service();

    abstract String remote_service();

    abstract int bucket();

    abstract UUID ts();

    abstract String trace_id();
  }

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final boolean strictTraceId;

    Factory(Session session, boolean strictTraceId) {
      this.session = session;
      this.preparedStatement =
        session.prepare(QueryBuilder.insertInto(TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE)
          .value("service", QueryBuilder.bindMarker("service"))
          .value("remote_service", QueryBuilder.bindMarker("remote_service"))
          .value("bucket", QueryBuilder.bindMarker("bucket"))
          .value("ts", QueryBuilder.bindMarker("ts"))
          .value("trace_id", QueryBuilder.bindMarker("trace_id")));
      this.strictTraceId = strictTraceId;
    }

    Input newInput(String service, String remote_service, int bucket, UUID ts, String trace_id) {
      return new AutoValue_InsertTraceByServiceRemoteService_Input(
        service,
        remote_service,
        bucket,
        ts,
        !strictTraceId && trace_id.length() == 32 ? trace_id.substring(16) : trace_id);
    }

    Call<Void> create(Input input) {
      return new InsertTraceByServiceRemoteService(this, input);
    }
  }

  final Factory factory;
  final Input input;

  InsertTraceByServiceRemoteService(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.preparedStatement.bind()
      .setString("service", input.service())
      .setString("remote_service", input.remote_service())
      .setInt("bucket", input.bucket())
      .setUUID("ts", input.ts())
      .setString("trace_id", input.trace_id()));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertTraceByServiceRemoteService");
  }

  @Override public InsertTraceByServiceRemoteService clone() {
    return new InsertTraceByServiceRemoteService(factory, input);
  }
}
