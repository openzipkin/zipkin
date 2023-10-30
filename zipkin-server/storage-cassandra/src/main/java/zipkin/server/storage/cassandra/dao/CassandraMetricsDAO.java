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

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import zipkin.server.storage.cassandra.CassandraClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class CassandraMetricsDAO extends CassandraCqlExecutor implements IMetricsDAO {
  private final CassandraClient client;
  private final StorageBuilder<Metrics> storageBuilder;

  public CassandraMetricsDAO(CassandraClient client, StorageBuilder<Metrics> storageBuilder) {
    this.client = client;
    this.storageBuilder = storageBuilder;
  }

  @Override
  public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws Exception {
    final List<String> ids = metrics.stream().map(m -> TableHelper.generateId(model, m.id().build())).collect(toList());
    final List<StorageData> storageDataList = getByIDs(client, model.getName(), ids, storageBuilder);
    final List<Metrics> result = new ArrayList<>(storageDataList.size());
    for (StorageData storageData : storageDataList) {
      result.add((Metrics) storageData);
    }
    return result;
  }

  @Override
  public InsertRequest prepareBatchInsert(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
    return getInsertExecutor(model, metrics, metrics.getTimeBucket(), storageBuilder, new HashMapConverter.ToStorage(), callback);
  }

  @Override
  public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
    return getUpdateExecutor(model, metrics, metrics.getTimeBucket(), storageBuilder, callback);
  }
}
