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

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
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
import java.util.concurrent.ConcurrentHashMap;

public class CassandraBatchDAO implements IBatchDAO {
  static final Logger LOG = LoggerFactory.getLogger(CassandraBatchDAO.class);

  private CassandraClient client;
  private final DataCarrier<PrepareRequest> dataCarrier;

  private static final ConcurrentHashMap<String, PreparedStatement> PREPARED_STATEMENT_MAP = new ConcurrentHashMap<>();

  public CassandraBatchDAO(CassandraClient client, int asyncBatchPersistentPoolSize) {
    this.client = client;
    String name = "CASSANDRA_ASYNCHRONOUS_BATCH_PERSISTENT";
    if (LOG.isDebugEnabled()) {
      LOG.debug("CASSANDRA_ASYNCHRONOUS_BATCH_PERSISTENT poolSize: {}", asyncBatchPersistentPoolSize);
    }
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

    List<PrepareRequest> cqls = new ArrayList<>();
    prepareRequests.forEach(prepareRequest -> {
      cqls.add(prepareRequest);
      CQLExecutor cqlExecutor = (CQLExecutor) prepareRequest;
      if (!CollectionUtils.isEmpty(cqlExecutor.getAdditionalCQLs())) {
        cqls.addAll(cqlExecutor.getAdditionalCQLs());
      }
    });

    if (LOG.isDebugEnabled()) {
      LOG.debug("to execute sql statements execute, data size: {}", cqls.size());
    }
    if (CollectionUtils.isEmpty(cqls)) {
      return CompletableFuture.completedFuture(null);
    }

    final long start = System.currentTimeMillis();
    final boolean isInsert = cqls.get(0) instanceof InsertRequest;
    try {
      for (PrepareRequest cql : cqls) {
        final CQLExecutor executor = (CQLExecutor) cql;
        if (LOG.isDebugEnabled()) {
          LOG.debug("Executing CQL: {}", executor.getCql());
          LOG.debug("CQL parameters: {}", executor.getParams());
        }

        final PreparedStatement stmt = PREPARED_STATEMENT_MAP.computeIfAbsent(executor.getCql(), e -> client.getSession().prepare(e));

        final BoundStatement boundStatement = stmt.bind(((CQLExecutor) cql).getParams().toArray());
        client.getSession().executeAsync(boundStatement).thenAccept(result -> {
          if (isInsert) {
            ((InsertRequest) executor).onInsertCompleted();
          } else if (!result.wasApplied()) {
            ((UpdateRequest) executor).onUpdateFailure();
          };
        });
      }
    } catch (Exception e) {
      // Just to avoid one execution failure makes the rest of batch failure.
      LOG.error(e.getMessage(), e);
    }

    if (LOG.isDebugEnabled()) {
      long end = System.currentTimeMillis();
      long cost = end - start;
      LOG.debug("execute sync sql statements done, data size: {}, cost:{}ms", prepareRequests.size(), cost);
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
