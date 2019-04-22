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
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;
import zipkin2.v1.V1Span;

final class InsertTrace extends ResultSetFutureCall<Void> {
  private static final Logger LOG = LoggerFactory.getLogger(InsertTrace.class);

  @AutoValue
  abstract static class Input {
    abstract long trace_id();

    abstract long ts();

    abstract String span_name();

    abstract byte[] span();
  }

  static class Factory {
    final Session session;
    final PreparedStatement preparedStatement;
    final TimestampCodec timestampCodec;
    final boolean dateTieredCompactionStrategy;

    Factory(Session session, Schema.Metadata metadata, int spanTtl) {
      this.session = session;
      this.timestampCodec = new TimestampCodec(session);
      Insert insertQuery =
          QueryBuilder.insertInto("traces")
              .value("trace_id", QueryBuilder.bindMarker("trace_id"))
              .value("ts", QueryBuilder.bindMarker("ts"))
              .value("span_name", QueryBuilder.bindMarker("span_name"))
              .value("span", QueryBuilder.bindMarker("span"));
      if (spanTtl > 0) insertQuery.using(QueryBuilder.ttl(spanTtl));

      this.dateTieredCompactionStrategy =
          metadata.compactionClass.contains("DateTieredCompactionStrategy");
      this.preparedStatement = session.prepare(insertQuery);
    }

    Input newInput(V1Span v1, byte[] v1Bytes, long ts_micro) {
      String span_name =
          String.format(
              "%s%d_%d_%d",
              v1.traceIdHigh() == 0 ? "" : v1.traceIdHigh() + "_",
              v1.id(),
              v1.annotations().hashCode(),
              v1.binaryAnnotations().hashCode());

      // If we couldn't guess the timestamp, that probably means that there was a missing timestamp.
      if (0L == ts_micro && dateTieredCompactionStrategy) {
        LOG.warn(
            "Span {} in trace {} had no timestamp. "
                + "If this happens a lot consider switching back to SizeTieredCompactionStrategy for "
                + "{}.traces",
            span_name,
            v1.traceId(),
            session.getLoggedKeyspace());
      }

      return new AutoValue_InsertTrace_Input(v1.traceId(), ts_micro, span_name, v1Bytes);
    }

    Call<Void> create(Input span) {
      return new InsertTrace(this, span);
    }
  }

  final Factory factory;
  final Input input;

  InsertTrace(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override
  protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(
        factory
            .preparedStatement
            .bind()
            .setLong("trace_id", input.trace_id())
            .setBytesUnsafe("ts", factory.timestampCodec.serialize(input.ts()))
            .setString("span_name", input.span_name())
            .setBytes("span", ByteBuffer.wrap(input.span())));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override
  public String toString() {
    return input.toString().replace("Input", "InsertTrace");
  }

  @Override
  public InsertTrace clone() {
    return new InsertTrace(factory, input);
  }
}
