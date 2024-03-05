/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.google.auto.value.AutoValue;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_SPAN;

final class InsertTraceByServiceSpan extends ResultSetFutureCall<Void> {
  @AutoValue abstract static class Input {
    abstract String service();

    abstract String span();

    abstract int bucket();

    abstract UUID ts();

    abstract String trace_id();

    abstract long duration();
  }

  static final class Factory {
    final CqlSession session;
    final PreparedStatement preparedStatement;
    final boolean strictTraceId;

    Factory(CqlSession session, boolean strictTraceId) {
      this.session = session;
      this.preparedStatement = session.prepare("INSERT INTO " + TABLE_TRACE_BY_SERVICE_SPAN
        + " (service,span,bucket,ts,trace_id,duration)"
        + " VALUES (?,?,?,?,?,?)");
      this.strictTraceId = strictTraceId;
    }

    /**
     * While {@link zipkin2.Span#duration()} cannot be zero, zero duration in milliseconds is
     * permitted, as it implies the span took less than 1 millisecond (1-999us).
     */
    Input newInput(
      String service, String span, int bucket, UUID ts, String trace_id, long durationMillis) {
      return new AutoValue_InsertTraceByServiceSpan_Input(
        service,
        span,
        bucket,
        ts,
        !strictTraceId && trace_id.length() == 32 ? trace_id.substring(16) : trace_id,
        durationMillis);
    }

    Call<Void> create(Input input) {
      return new InsertTraceByServiceSpan(this, input);
    }
  }

  final Factory factory;
  final Input input;

  InsertTraceByServiceSpan(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected CompletionStage<AsyncResultSet> newCompletionStage() {
    BoundStatementBuilder bound = factory.preparedStatement.boundStatementBuilder()
      .setString(0, input.service())
      .setString(1, input.span())
      .setInt(2, input.bucket())
      .setUuid(3, input.ts())
      .setString(4, input.trace_id());

    if (0L != input.duration()) bound.setLong(5, input.duration());

    return factory.session.executeAsync(bound.build());
  }

  @Override public Void map(AsyncResultSet input) {
    return null;
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertTraceByServiceSpan");
  }

  @Override public InsertTraceByServiceSpan clone() {
    return new InsertTraceByServiceSpan(factory, input);
  }
}
