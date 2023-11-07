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

import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class TraceIDByAnnotationQueryExecutor extends BaseQueryExecutor {
  private final Query<String> query;
  public TraceIDByAnnotationQueryExecutor(CassandraClient client, CassandraTableHelper tableHelper) {
    super(client, tableHelper);
    this.query = buildQuery(
        () -> "select " + ZipkinSpanRecord.TRACE_ID +
            " from " + ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE +
            " where " + ZipkinSpanRecord.QUERY + " = ?" +
            " and " + ZipkinSpanRecord.TIME_BUCKET + " >= ?" +
            " and " + ZipkinSpanRecord.TIME_BUCKET + " <= ?",
        row -> row.getString(ZipkinSpanRecord.TRACE_ID)
    );
  }

  public CompletionStage<List<String>> asyncGet(String query, long startTimeBucket, long endTimeBucket) {
    return executeAsync(this.query, query, startTimeBucket, endTimeBucket);
  }
}
