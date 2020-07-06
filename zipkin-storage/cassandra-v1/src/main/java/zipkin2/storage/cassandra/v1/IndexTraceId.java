/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.auto.value.AutoValue;
import java.util.concurrent.TimeUnit;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.cassandra.internal.call.DeduplicatingVoidCallFactory;
import zipkin2.storage.cassandra.internal.call.ResultSetFutureCall;

/**
 * Inserts index rows into a Cassandra table. This skips entries that don't improve results based on
 * {@link QueryRequest#endTs()} and {@link QueryRequest#lookback()}. For example, it doesn't insert
 * rows that only vary on timestamp and exist between timestamps of existing rows.
 */
final class IndexTraceId extends ResultSetFutureCall<Void> {
  @AutoValue
  abstract static class Input {
    static Input create(String partitionKey, long timestamp, long traceId) {
      return new AutoValue_IndexTraceId_Input(partitionKey, timestamp, traceId);
    }

    abstract String partitionKey(); // ends up as a partition key, ignoring bucketing

    abstract long ts(); // microseconds at millis precision

    abstract long trace_id(); // clustering key
  }

  static abstract class Factory extends DeduplicatingVoidCallFactory<Input> {
    final Session session;
    final TraceIdIndexer.Factory indexerFactory;
    final int bucketCount;
    final PreparedStatement preparedStatement;
    final TimestampCodec timestampCodec;

    Factory(CassandraStorage storage, String table, int indexTtl) {
      super(TimeUnit.SECONDS.toMillis(storage.indexCacheTtl), storage.indexCacheMax);
      session = storage.session();
      indexerFactory =
        new TraceIdIndexer.Factory(table, TimeUnit.SECONDS.toNanos(storage.indexCacheTtl),
          storage.indexCacheMax);
      bucketCount = storage.bucketCount;
      Insert insertQuery = declarePartitionKey(QueryBuilder.insertInto(table)
        .value("ts", QueryBuilder.bindMarker("ts"))
        .value("trace_id", QueryBuilder.bindMarker("trace_id")));
      if (indexTtl > 0) insertQuery.using(QueryBuilder.ttl(indexTtl));
      preparedStatement = session.prepare(insertQuery);
      timestampCodec = new TimestampCodec(session);
    }

    abstract Insert declarePartitionKey(Insert insert);

    abstract BoundStatement bindPartitionKey(BoundStatement bound, String partitionKey);

    @Override protected IndexTraceId newCall(Input input) {
      return new IndexTraceId(this, input);
    }

    TraceIdIndexer newIndexer() {
      return indexerFactory.newIndexer();
    }

    @Override public void clear() {
      super.clear();
      indexerFactory.clear();
    }
  }

  final Factory factory;
  final Input input;

  IndexTraceId(Factory factory, Input input) {
    this.factory = factory;
    this.input = input;
  }

  @Override protected ResultSetFuture newFuture() {
    return factory.session.executeAsync(factory.bindPartitionKey(
      factory.preparedStatement.bind()
        .setLong("trace_id", input.trace_id())
        .setBytesUnsafe("ts", factory.timestampCodec.serialize(input.ts())), input.partitionKey()));
  }

  @Override public Void map(ResultSet input) {
    return null;
  }

  @Override public String toString() {
    return input.toString().replace("Input", factory.getClass().getSimpleName());
  }

  @Override public IndexTraceId clone() {
    return new IndexTraceId(factory, input);
  }
}
