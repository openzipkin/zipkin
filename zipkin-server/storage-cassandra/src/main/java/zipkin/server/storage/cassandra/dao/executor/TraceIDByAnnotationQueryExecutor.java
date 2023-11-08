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
import org.apache.skywalking.oap.server.library.util.StringUtil;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class TraceIDByAnnotationQueryExecutor extends BaseQueryExecutor {
  private final Query<String> query;
  private final Query<String> queryWithService;
  public TraceIDByAnnotationQueryExecutor(CassandraClient client, CassandraTableHelper tableHelper) {
    super(client, tableHelper);
    String querySuffix = "annotation_query LIKE ?"
        + " AND " + ZipkinSpanRecord.TIMESTAMP + ">=?"
        + " AND " + ZipkinSpanRecord.TIMESTAMP + "<=?"
        + " LIMIT ?"
        + " ALLOW FILTERING";

    this.query = buildQuery(() -> "select trace_id from " + ZipkinSpanRecord.INDEX_NAME + " where " + querySuffix,
        row -> row.getString(ZipkinSpanRecord.TRACE_ID)
    );
    this.queryWithService = buildQuery(() -> "select trace_id from " + ZipkinSpanRecord.INDEX_NAME + " where " +
            ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME + " = ? and " + querySuffix,
        row -> row.getString(ZipkinSpanRecord.TRACE_ID)
    );
  }

  public CompletionStage<List<String>> asyncGet(String serviceName, String query, long startTimeBucket, long endTimeBucket, int size) {
    if (StringUtil.isNotEmpty(serviceName)) {
      return executeAsync(this.queryWithService, serviceName, query, startTimeBucket, endTimeBucket, size);
    }
    return executeAsync(this.query, query, startTimeBucket, endTimeBucket, size);
  }
}
