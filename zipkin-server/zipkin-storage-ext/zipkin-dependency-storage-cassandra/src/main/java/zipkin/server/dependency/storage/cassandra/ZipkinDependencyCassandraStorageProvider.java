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

package zipkin.server.dependency.storage.cassandra;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import zipkin.server.dependency.IZipkinDependencyQueryDAO;
import zipkin.server.dependency.ZipkinDependencyModule;
import zipkin.server.storage.cassandra.CassandraClient;
import zipkin.server.storage.cassandra.CassandraConfig;
import zipkin.server.storage.cassandra.CassandraProvider;

import java.lang.reflect.Field;

public class ZipkinDependencyCassandraStorageProvider extends ModuleProvider {
  private CassandraConfig config;
  private ZipkinDependencyCassandraQueryDAO queryDAO;

  @Override
  public String name() {
    return "cassandra3";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinDependencyModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<CassandraConfig>() {

      @Override
      public Class<CassandraConfig> type() {
        return CassandraConfig.class;
      }

      @Override
      public void onInitialized(CassandraConfig initialized) {
        config = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    this.queryDAO = new ZipkinDependencyCassandraQueryDAO();
    this.registerServiceImplementation(IZipkinDependencyQueryDAO.class, queryDAO);
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    CassandraProvider provider =
        (CassandraProvider) getManager().find(StorageModule.NAME).provider();
    try {
      Field field = CassandraProvider.class.getDeclaredField("client");
      field.setAccessible(true);
      CassandraClient client = (CassandraClient) field.get(provider);
      queryDAO.setClient(client);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new ModuleStartException("Failed to get CassandraStorageClient.", e);
    }
  }

  @Override
  public String[] requiredModules() {
    return new String[] {CoreModule.NAME, StorageModule.NAME};
  }
}
