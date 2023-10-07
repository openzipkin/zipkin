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

import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.datacarrier.DataCarrier;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.server.storage.cassandra.CQLExecutor;
import zipkin.server.storage.cassandra.CassandraClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CassandraBatchDAO implements IBatchDAO {
  static final Logger LOG = LoggerFactory.getLogger(CassandraBatchDAO.class);

  private CassandraClient client;
  private final DataCarrier<PrepareRequest> dataCarrier;
  private final int maxBatchSqlSize;

  public CassandraBatchDAO(CassandraClient client, int maxBatchCqlSize, int asyncBatchPersistentPoolSize) {
    this.client = client;
    String name = "CASSANDRA_ASYNCHRONOUS_BATCH_PERSISTENT";
    if (LOG.isDebugEnabled()) {
      LOG.debug("CASSANDRA_ASYNCHRONOUS_BATCH_PERSISTENT poolSize: {}, maxBatchCqlSize:{}", asyncBatchPersistentPoolSize, maxBatchCqlSize);
    }
    this.maxBatchSqlSize = maxBatchCqlSize;
    this.dataCarrier = new DataCarrier<>(name, asyncBatchPersistentPoolSize, 10000);
    this.dataCarrier.consume(new CassandraBatchConsumer(this), asyncBatchPersistentPoolSize, 20);
  }

  @Override
  public void insert(InsertRequest insertRequest) {
    this.dataCarrier.produce(insertRequest);
  }

  @Override
  public CompletableFuture<Void> flush(List<PrepareRequest> prepareRequests) {
    if (CollectionUtils.isEmpty(prepareRequests)) {
      return CompletableFuture.completedFuture(null);
    }

    List<PrepareRequest> sqls = new ArrayList<>();
    prepareRequests.forEach(prepareRequest -> {
      sqls.add(prepareRequest);
      CQLExecutor cqlExecutor = (CQLExecutor) prepareRequest;
      if (!CollectionUtils.isEmpty(cqlExecutor.getAdditionalCQLs())) {
        sqls.addAll(cqlExecutor.getAdditionalCQLs());
      }
    });

    if (LOG.isDebugEnabled()) {
      LOG.debug("to execute sql statements execute, data size: {}, maxBatchSqlSize: {}", sqls.size(), maxBatchSqlSize);
    }

    try {
      final BatchCQLExecutor batchSQLExecutor = new BatchCQLExecutor(client, sqls);
      batchSQLExecutor.invoke(maxBatchSqlSize);
    } catch (Exception e) {
      // Just to avoid one execution failure makes the rest of batch failure.
      LOG.error(e.getMessage(), e);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("execute sql statements done, data size: {}, maxBatchSqlSize: {}", prepareRequests.size(), maxBatchSqlSize);
    }
    return CompletableFuture.completedFuture(null);
  }


  private static class CassandraBatchConsumer implements IConsumer<PrepareRequest> {

    private final CassandraBatchDAO batchDAO;

    private CassandraBatchConsumer(CassandraBatchDAO batchDAO) {
      this.batchDAO = batchDAO;
    }

    @Override
    public void consume(List<PrepareRequest> prepareRequests) {
      batchDAO.flush(prepareRequests);
    }

    @Override
    public void onError(List<PrepareRequest> prepareRequests, Throwable t) {
      LOG.error(t.getMessage(), t);
    }
  }
}
