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

import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import zipkin.server.storage.cassandra.CassandraClient;

public class CassandraStorageDAO implements StorageDAO {
  private final CassandraClient client;

  public CassandraStorageDAO(CassandraClient client) {
    this.client = client;
  }

  @Override
  public IMetricsDAO newMetricsDao(StorageBuilder storageBuilder) {
    return new CassandraMetricsDAO(client, storageBuilder);
  }

  @Override
  public IRecordDAO newRecordDao(StorageBuilder storageBuilder) {
    return new CassandraRecordDAO(storageBuilder);
  }

  @Override
  public INoneStreamDAO newNoneStreamDao(StorageBuilder storageBuilder) {
    throw new IllegalStateException("Cassandra does not support NoneStreamDAO");
  }

  @Override
  public IManagementDAO newManagementDao(StorageBuilder storageBuilder) {
    throw new IllegalStateException("Cassandra does not support ManagementDAO");
  }
}
