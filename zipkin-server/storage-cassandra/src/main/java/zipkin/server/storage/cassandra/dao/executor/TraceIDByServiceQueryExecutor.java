/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.storage.cassandra.dao.executor;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;
import zipkin.server.storage.cassandra.dao.CassandraTableExtension;

import javax.annotation.CheckReturnValue;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class TraceIDByServiceQueryExecutor extends BaseQueryExecutor {
  private final Query<String> queryWithRemoteService;
  private final Query<String> queryWithSpan;
  private final Query<String> queryWithSpanAndDuration;

  public TraceIDByServiceQueryExecutor(CassandraClient client, CassandraTableHelper tableHelper) {
    super(client, tableHelper);
    String baseCql = "select trace_id from " + CassandraTableExtension.TABLE_TRACE_BY_SERVICE_SPAN
        + " where service=? and span=? and bucket=? and ts>=? and ts<=? ";
    this.queryWithRemoteService = buildQuery(
        () -> "select trace_id from " + CassandraTableExtension.TABLE_TRACE_BY_SERVICE_REMOTE_SERVICE
            + " where service=? and remote_service=? and bucket=? and ts>=? and ts<=?",
        row -> row.getString(0)
    );
    this.queryWithSpan = buildQuery(
        () -> baseCql + " limit ?",
        row -> row.getString(0)
    );
    this.queryWithSpanAndDuration = buildQuery(
        () -> baseCql + " and duration>=? and duration<=? limit ?",
        row -> row.getString(0)
    );
  }

  public CompletionStage<List<String>> asyncWithRemoteService(
      String serviceName, String remoteService, int bucket, UUID tsStart, UUID tsEnd) {
    return executeAsync(queryWithRemoteService, serviceName, remoteService, bucket, tsStart, tsEnd);
  }

  public CompletionStage<List<String>> asyncWithSpan(
      String serviceName, String spanName, int bucket, UUID tsStart, UUID tsEnd, int limit) {
    return asyncSpan0(queryWithSpan, serviceName, spanName, bucket, tsStart, tsEnd, null, null, limit);
  }

  public CompletionStage<List<String>> asyncWithSpanAndDuration(
      String serviceName, String spanName, int bucket, UUID tsStart, UUID tsEnd, long durationStart, long durationEnd, int limit) {
    return asyncSpan0(queryWithSpanAndDuration, serviceName, spanName, bucket, tsStart, tsEnd, durationStart, durationEnd, limit);
  }

  @SuppressWarnings("CheckReturnValue")
  private CompletionStage<List<String>> asyncSpan0(
      Query<String> query, String serviceName, String spanName, int bucket, UUID tsStart, UUID tsEnd, Long durationStart, Long durationEnd, int limit) {
    return this.executeAsyncWithCustomizeStatement(query, stmt -> {
      int i = 0;
      final BoundStatementBuilder bound = stmt.boundStatementBuilder()
          .setString(i++, serviceName)
          .setString(i++, spanName)
          .setInt(i++, bucket)
          .setUuid(i++, tsStart)
          .setUuid(i++, tsEnd);

      if (durationStart != null) {
        bound.setLong(i++, durationStart)
            .setLong(i++, durationEnd);
      }

      bound.setInt(i, limit)
          .setPageSize(limit);

      return bound.build();
    });
  }

}
