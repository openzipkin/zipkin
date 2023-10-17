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

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

public class CassandraTableHelper extends TableHelper {
  private ModuleManager moduleManager;
  private final CassandraClient client;

  public CassandraTableHelper(ModuleManager moduleManager, CassandraClient client) {
    super(moduleManager, null);
    this.moduleManager = moduleManager;
    this.client = client;
  }

  public String getTableForRead(String modelName) {
    final Model model = TableMetaInfo.get(modelName);
    return getTableName(model);
  }

  ConfigService getConfigService() {
    return moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class);
  }
}
