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

package zipkin.server.storage.cassandra.dao;

import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.server.storage.cassandra.CQLExecutor;
import zipkin.server.storage.cassandra.CassandraClient;

import java.util.ArrayList;
import java.util.List;

public class BatchCQLExecutor implements InsertRequest, UpdateRequest {
  static final Logger LOG = LoggerFactory.getLogger(BatchCQLExecutor.class);

  private final CassandraClient client;
  private final List<PrepareRequest> requests;

  public BatchCQLExecutor(CassandraClient client, List<PrepareRequest> requests) {
    this.client = client;
    this.requests = requests;
  }

  public void invoke(int maxBatchCqlSize) throws Exception {
    if (LOG.isDebugEnabled()) {
      LOG.debug("execute cql batch. sql by key size: {}", requests.size());
    }
    if (requests.size() == 0) {
      return;
    }
    final String sql = requests.get(0).toString();
    final BatchStatementBuilder batchBuilder = BatchStatement.builder(BatchType.LOGGED);
    int pendingCount = 0;
    final ArrayList<CQLExecutor> executors = new ArrayList<>();
    for (PrepareRequest request : requests) {
      final CQLExecutor executor = (CQLExecutor) request;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Executing CQL: {}", executor.getCql());
        LOG.debug("CQL parameters: {}", executor.getParams());
      }
      executors.add(executor);
      batchBuilder.addStatement(client.getSession().prepare(executor.getCql()).bind(executor.getParams().toArray()));
      if (batchBuilder.getStatementsCount() == maxBatchCqlSize) {
        executeBatch(maxBatchCqlSize, batchBuilder.build(), executors, sql);
        client.getSession().execute(batchBuilder.build());
        batchBuilder.clearStatements();
        executors.clear();
        pendingCount = 0;
      } else {
        pendingCount++;
      }
    }

    if (pendingCount > 0) {
      executeBatch(pendingCount, batchBuilder.build(), executors, sql);
      batchBuilder.clearStatements();
    }
  }

  private void executeBatch(int pendingCount, BatchStatement stmt, List<CQLExecutor> bulkExecutors, String sql) {
    final long start = System.currentTimeMillis();
    boolean success = true;
    try {
      client.getSession().execute(stmt);
    } catch (Exception e) {
      success = false;
      LOG.warn("execute batch cql failure", e);
    }
    final boolean isInsert = bulkExecutors.get(0) instanceof InsertRequest;
    for (CQLExecutor executor : bulkExecutors) {
      if (isInsert) {
        ((InsertRequest) executor).onInsertCompleted();
      } else if (!success) {
        ((UpdateRequest) executor).onUpdateFailure();
      }
    }
    if (LOG.isDebugEnabled()) {
      long end = System.currentTimeMillis();
      long cost = end - start;
      LOG.debug("execute batch cql, batch size: {}, cost:{}ms, sql: {}", pendingCount, cost, sql);
    }
  }

  @Override
  public void onInsertCompleted() {
    throw new UnexpectedException("BatchCQLExecutor.onInsertCompleted should not be called");
  }

  @Override
  public void onUpdateFailure() {
    throw new UnexpectedException("BatchCQLExecutor.onUpdateFailure should not be called");
  }
}
