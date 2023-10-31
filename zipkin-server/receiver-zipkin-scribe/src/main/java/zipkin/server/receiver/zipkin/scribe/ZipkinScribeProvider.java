/*
 * Copyright 2015-2019 The OpenZipkin Authors
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

package zipkin.server.receiver.zipkin.scribe;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;

public class ZipkinScribeProvider extends ModuleProvider {
  private ZipkinScribeConfig moduleConfig;
  private SpanForwardService spanForward;

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinScribeModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<ZipkinScribeConfig>() {
      @Override
      public Class<ZipkinScribeConfig> type() {
        return ZipkinScribeConfig.class;
      }

      @Override
      public void onInitialized(ZipkinScribeConfig initialized) {
        moduleConfig = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    this.spanForward = getManager().find(ZipkinReceiverModule.NAME).provider().getService(SpanForwardService.class);
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    NettyScribeServer server = new NettyScribeServer(moduleConfig.getPort(),
        new ScribeSpanConsumer(this.spanForward, moduleConfig.getCategory(), getManager()));
    server.start();
  }

  @Override
  public String[] requiredModules() {
    return new String[0];
  }
}
