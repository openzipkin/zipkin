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
import zipkin2.Span;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.stream.Collectors.toList;

public class MultipleTraceQueryExecutor extends BaseQueryExecutor {
  private final Query<Span> query;
  public MultipleTraceQueryExecutor(CassandraClient client, CassandraTableHelper tableHelper) {
    super(client, tableHelper);
    this.query = buildQuery(
        () -> "select * from " + tableHelper.getTableForRead(ZipkinSpanRecord.INDEX_NAME) + " where " + ZipkinSpanRecord.TRACE_ID + " = ?",
        this::buildSpan
    );
  }

  public List<List<Span>> get(Set<String> traceIds) {
    return traceIds.stream().map(s -> executeAsync(query, s))
        .map(CompletionStage::toCompletableFuture).map(CompletableFuture::join).collect(toList());
  }
}
