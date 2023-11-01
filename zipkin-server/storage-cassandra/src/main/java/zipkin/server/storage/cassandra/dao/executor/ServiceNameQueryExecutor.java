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

import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceTraffic;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraTableHelper;

import java.util.List;

public class ServiceNameQueryExecutor extends BaseQueryExecutor {
  private final Query<String> query;

  public ServiceNameQueryExecutor(CassandraClient client, CassandraTableHelper tableHelper) {
    super(client, tableHelper);
    query = buildQuery(
        () -> "select " + ZipkinServiceTraffic.SERVICE_NAME + " from " +
            tableHelper.getTableForRead(ZipkinServiceTraffic.INDEX_NAME) + " limit " + NAME_QUERY_MAX_SIZE,
        resultSet -> resultSet.getString(ZipkinServiceTraffic.SERVICE_NAME)
    );
  }

  public List<String> get() {
    return executeSync(query);
  }
}
