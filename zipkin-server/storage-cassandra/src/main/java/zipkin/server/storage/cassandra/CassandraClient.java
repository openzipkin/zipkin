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

package zipkin.server.storage.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.auth.AuthProvider;
import com.datastax.oss.driver.api.core.config.DriverOption;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.internal.core.auth.ProgrammaticPlainTextAuthProvider;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.util.HealthChecker;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.server.storage.cassandra.internal.SessionBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONNECTION_MAX_REQUESTS;
import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE;

public class CassandraClient implements Client {
  static final Logger LOG = LoggerFactory.getLogger(CassandraClient.class);

  public static final String RECORD_UNIQUE_UUID_COLUMN = "uuid_unique";

  private final CassandraConfig config;
  private final DelegatedHealthChecker healthChecker;

  private volatile CqlSession cqlSession;

  public CassandraClient(CassandraConfig config) {
    this.config = config;
    this.healthChecker = new DelegatedHealthChecker();
  }

  public KeyspaceMetadata getMetadata() {
    return cqlSession.getMetadata().getKeyspace(config.getKeyspace()).orElse(null);
  }

  public CqlSession getSession() {
    return cqlSession;
  }

  public int getDefaultTtl(String table) {
    return (int) getMetadata().getTable(table)
        .map(TableMetadata::getOptions)
        .flatMap(o -> Optional.ofNullable(o.get(CqlIdentifier.fromCql("default_time_to_live"))))
        .orElse(0);
  }

  public <T> List<T> executeQuery(String cql, ResultHandler<T> resultHandler, Object... params) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing CQL: {}", cql);
      LOG.debug("CQL parameters: {}", Arrays.toString(params));
    }
    final BoundStatement stmt = cqlSession.prepare(cql).bind(params);
    final ResultSet resultSet = cqlSession.execute(stmt);
    healthChecker.health();
    if (resultHandler != null) {
      return StreamSupport.stream(resultSet.spliterator(), false)
          .map(resultHandler::handle).collect(Collectors.toList());
    }
    return null;
  }

  public <T> CompletionStage<List<T>> executeAsyncQuery(String cql, ResultHandler<T> resultHandler, Object... params) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing CQL: {}", cql);
      LOG.debug("CQL parameters: {}", Arrays.toString(params));
    }
    final BoundStatement stmt = cqlSession.prepare(cql).bind(params);
    final CompletionStage<AsyncResultSet> resultSet = cqlSession.executeAsync(stmt);
    healthChecker.health();
    if (resultHandler != null) {
      return resultSet.thenApply(s -> StreamSupport.stream(s.currentPage().spliterator(), false)
          .map(resultHandler::handle).collect(Collectors.toList()));
    }
    return null;
  }

  public void execute(String cql) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing CQL: {}", cql);
    }
    cqlSession.execute(cql);
    healthChecker.health();
  }

  public void registerChecker(HealthChecker healthChecker) {
    this.healthChecker.register(healthChecker);
  }

  @Override
  public void connect() throws Exception {
    AuthProvider authProvider = null;
    if (StringUtil.isNotEmpty(config.getUsername())) {
      authProvider = new ProgrammaticPlainTextAuthProvider(config.getUsername(), config.getPassword());
    }
    this.cqlSession = SessionBuilder.buildSession(config.getContactPoints(),
        config.getLocalDc(),
        poolingOptions(),
        authProvider,
        config.getUseSsl());

    // create keyspace if needs
    final String keyspace = config.getKeyspace();
    KeyspaceMetadata keyspaceMetadata = this.cqlSession.getMetadata().getKeyspace(keyspace).orElse(null);
    if (keyspaceMetadata == null) {
      String createKeyspaceCql = String.format(
          "CREATE KEYSPACE IF NOT EXISTS %s " +
              "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'} " +
              "AND durable_writes = false;",
          keyspace);
      this.cqlSession.execute(createKeyspaceCql);
    }

    this.cqlSession.execute("USE " + keyspace);
  }

  @Override
  public void shutdown() throws IOException {
  }

  private Map<DriverOption, Integer> poolingOptions() {
    Map<DriverOption, Integer> result = new LinkedHashMap<>();
    result.put(CONNECTION_POOL_LOCAL_SIZE, config.getMaxConnections());
    result.put(CONNECTION_MAX_REQUESTS, 40960 / config.getMaxConnections());
    return result;
  }

  @FunctionalInterface
  public interface ResultHandler<T> {
    T handle(Row resultSet);
  }
}
