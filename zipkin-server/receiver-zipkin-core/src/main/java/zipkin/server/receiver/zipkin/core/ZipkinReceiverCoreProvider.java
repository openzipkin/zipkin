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

package zipkin.server.receiver.zipkin.core;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;

public class ZipkinReceiverCoreProvider extends ModuleProvider {
  private ZipkinReceiverCoreConfig moduleConfig;

  @Override
  public String name() {
    return "zipkin";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinReceiverModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<ZipkinReceiverCoreConfig>() {

      @Override
      public Class<ZipkinReceiverCoreConfig> type() {
        return ZipkinReceiverCoreConfig.class;
      }

      @Override
      public void onInitialized(ZipkinReceiverCoreConfig initialized) {
        moduleConfig = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    final ZipkinReceiverConfig config = new ZipkinReceiverConfig();
    config.setSearchableTracesTags(moduleConfig.getSearchableTracesTags());
    config.setSampleRate((int) (moduleConfig.getTraceSampleRate() * 10000));

    this.registerServiceImplementation(SpanForwardService.class, new SpanForwardCore(config, getManager()));
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
  }

  @Override
  public String[] requiredModules() {
    return new String[0];
  }
}
