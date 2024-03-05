/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.google.auto.value.AutoValue;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
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

  static final class Factory {
    final CqlSession session;
    final PreparedStatement preparedStatement;
    final boolean strictTraceId;

    Factory(CqlSession session, boolean strictTraceId) {
      this.session = session;
      this.preparedStatement =
        session.prepare("INSERT INTO " + TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE
          + " (service,remote_service,bucket,ts,trace_id)"
          + " VALUES (?,?,?,?,?)");
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

  @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
    return factory.session.executeAsync(factory.preparedStatement.boundStatementBuilder()
      .setString(0, input.service())
      .setString(1, input.remote_service())
      .setInt(2, input.bucket())
      .setUuid(3, input.ts())
      .setString(4, input.trace_id()).build());
  }

  @Override public Void map(AsyncResultSet input) {
    return null;
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertTraceByServiceRemoteService");
  }

  @Override public InsertTraceByServiceRemoteService clone() {
    return new InsertTraceByServiceRemoteService(factory, input);
  }
}
