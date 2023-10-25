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

package zipkin.server.dependency.storage.banyandb;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageProvider;
import zipkin.server.dependency.IZipkinDependencyQueryDAO;
import zipkin.server.dependency.ZipkinDependencyModule;

import java.lang.reflect.Field;

public class ZipkinDependencyBanyanDBStorageProvider extends ModuleProvider {
  private BanyanDBStorageConfig config;
  private ZipkinDependencyBanyanDBQueryDAO queryDAO;

  @Override
  public String name() {
    return "banyandb";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinDependencyModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<BanyanDBStorageConfig>() {

      @Override
      public Class<BanyanDBStorageConfig> type() {
        return BanyanDBStorageConfig.class;
      }

      @Override
      public void onInitialized(BanyanDBStorageConfig initialized) {
        config = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    this.queryDAO = new ZipkinDependencyBanyanDBQueryDAO();
    this.registerServiceImplementation(IZipkinDependencyQueryDAO.class, this.queryDAO);
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {

  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    BanyanDBStorageProvider provider =
        (BanyanDBStorageProvider) getManager().find(StorageModule.NAME).provider();
    try {
      Field field = BanyanDBStorageProvider.class.getDeclaredField("client");
      field.setAccessible(true);
      BanyanDBStorageClient client = (BanyanDBStorageClient) field.get(provider);
      queryDAO.setClient(client);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new ModuleStartException("Failed to get BanyanDBStorageClient.", e);
    }
  }

  @Override
  public String[] requiredModules() {
    return new String[] {CoreModule.NAME, StorageModule.NAME};
  }
}
