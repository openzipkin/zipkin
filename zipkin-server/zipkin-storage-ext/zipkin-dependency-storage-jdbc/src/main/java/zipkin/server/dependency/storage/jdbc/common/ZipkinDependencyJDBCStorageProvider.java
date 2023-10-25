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

package zipkin.server.dependency.storage.jdbc.common;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCStorageProvider;
import zipkin.server.dependency.IZipkinDependencyQueryDAO;
import zipkin.server.dependency.ZipkinDependencyModule;
import zipkin.server.dependency.storage.jdbc.common.dao.ZipkinDependencyJDBCQueryDAO;

import java.lang.reflect.Field;

public abstract class ZipkinDependencyJDBCStorageProvider extends ModuleProvider {
  private JDBCStorageConfig config;
  private ZipkinDependencyJDBCQueryDAO queryDAO;

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinDependencyModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<JDBCStorageConfig>() {
      @Override
      public Class<JDBCStorageConfig> type() {
        return JDBCStorageConfig.class;
      }

      @Override
      public void onInitialized(JDBCStorageConfig initialized) {
        config = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    this.queryDAO = new ZipkinDependencyJDBCQueryDAO();
    this.registerServiceImplementation(IZipkinDependencyQueryDAO.class, queryDAO);
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {

  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    JDBCStorageProvider provider =
        (JDBCStorageProvider) getManager().find(StorageModule.NAME).provider();
    queryDAO.setClient(getFieldValue(provider, JDBCStorageProvider.class, "jdbcClient"));
    queryDAO.setTableHelper(getFieldValue(provider, JDBCStorageProvider.class, "tableHelper"));
  }

  private <T> T getFieldValue(Object from, Class fieldBelongClass, String fieldName) throws ModuleStartException {
    try {
      Field field = fieldBelongClass.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (T) field.get(from);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new ModuleStartException("Failed to get " + fieldName, e);
    }
  }

  @Override
  public String[] requiredModules() {
    return new String[] {CoreModule.NAME, StorageModule.NAME};
  }
}
