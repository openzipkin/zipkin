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

import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;

public class CassandraTableInstaller implements ModelCreator.CreatingListener {
  private final CassandraClient client;
  private final CassandraConfig config;

  public CassandraTableInstaller(CassandraClient client, CassandraConfig config) {
    this.client = client;
    this.config = config;
  }

  public void start() {
    try {
      if (!config.getEnsureSchema()) {
        return;
      }

      Schema.ensureExists(config.getKeyspace(), client.getSession());
    } finally {
      client.getSession().execute("USE " + config.getKeyspace());
      Schema.check(client.getSession(), config.getKeyspace());
    }
  }

  @Override
  public void whenCreating(Model model) throws StorageException {
    if (ZipkinSpanRecord.INDEX_NAME.equals(model.getName())) {
      // remove unnecessary columns
      for (int i = model.getColumns().size() - 1; i >= 0; i--) {
        final String columnName = model.getColumns().get(i).getColumnName().getStorageName();
        if (StorageData.TIME_BUCKET.equals(columnName) ||
            ZipkinSpanRecord.TIMESTAMP_MILLIS.equals(columnName) ||
            JDBCTableInstaller.TABLE_COLUMN.equals(columnName)) {
          model.getColumns().remove(i);
        }
      }
    }
    TableMetaInfo.addModel(model);
  }
}
