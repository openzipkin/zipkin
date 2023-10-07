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

import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;

import java.io.IOException;

public class CassandraRecordDAO extends CassandraCqlExecutor implements IRecordDAO {
  private final StorageBuilder<Record> storageBuilder;

  public CassandraRecordDAO(StorageBuilder<Record> storageBuilder) {
    this.storageBuilder = storageBuilder;
  }

  @Override
  public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
    return getInsertExecutor(model, record, record.getTimeBucket(), storageBuilder, new HashMapConverter.ToStorage(), null);
  }
}
