/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
import java.util.UUID;
import zipkin2.Call;
import zipkin2.internal.Nullable;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

import static zipkin2.storage.cassandra.Schema.TABLE_TRACE_BY_SERVICE_SPAN;

final class InsertTraceByServiceSpan extends ResultSetFutureCall {

  @AutoValue static abstract class Input {
    abstract String service();

    abstract String span();

    abstract int bucket();

    abstract UUID ts();

    abstract String trace_id();

    abstract long duration();
  }

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final boolean strictTraceId;

    Factory(Session session, boolean strictTraceId) {
      this.session = session;
      this.preparedStatement = session.prepare(QueryBuilder.insertInto(TABLE_TRACE_BY_SERVICE_SPAN)
        .value("service", QueryBuilder.bindMarker("service"))
        .value("span", QueryBuilder.bindMarker("span"))
        .value("bucket", QueryBuilder.bindMarker("bucket"))
        .value("ts", QueryBuilder.bindMarker("ts"))
        .value("trace_id", QueryBuilder.bindMarker("trace_id"))
        .value("duration", QueryBuilder.bindMarker("duration")));
      this.strictTraceId = strictTraceId;
    }

    /**
     * While {@link zipkin2.Span#duration} cannot be zero, zero duration in milliseconds is
     * permitted, as it implies the span took less than 1 millisecond (1-999us).
     */
    Input newInput(
      String service,
      String span,
      int bucket,
      UUID ts,
      String trace_id,
      long durationMillis
    ) {
      return new AutoValue_InsertTraceByServiceSpan_Input(
        service,
        span,
        bucket,
        ts,
        !strictTraceId && trace_id.length() == 32 ? trace_id.substring(16) : trace_id,
        durationMillis
      );
    }

    Call<ResultSet> create(Input input) {
      return new InsertTraceByServiceSpan(this, input);
    }
  }

  final Factory factory;
  final Input input;

  InsertTraceByServiceSpan(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    BoundStatement bound = factory.preparedStatement.bind()
      .setString("service", input.service())
      .setString("span", input.span())
      .setInt("bucket", input.bucket())
      .setUUID("ts", input.ts())
      .setString("trace_id", input.trace_id());

    if (0L != input.duration()) {
      bound.setLong("duration", input.duration());
    }
    return factory.session.executeAsync(bound);
  }

  @Override public String toString() {
    return input.toString().replace("Input", "InsertTraceByServiceSpan");
  }

  @Override public InsertTraceByServiceSpan clone() {
    return new InsertTraceByServiceSpan(factory, input);
  }
}
