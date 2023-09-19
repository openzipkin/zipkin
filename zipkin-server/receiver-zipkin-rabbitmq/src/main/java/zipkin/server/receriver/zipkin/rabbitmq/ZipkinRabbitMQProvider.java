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

package zipkin.server.receriver.zipkin.rabbitmq;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import zipkin.server.core.services.ZipkinConfigService;

public class ZipkinRabbitMQProvider extends ModuleProvider {
  private ZipkinRabbitMQConfig moduleConfig;
  private SpanForward spanForward;

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinRabbitMQModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<ZipkinRabbitMQConfig>() {
      @Override
      public Class<ZipkinRabbitMQConfig> type() {
        return ZipkinRabbitMQConfig.class;
      }

      @Override
      public void onInitialized(ZipkinRabbitMQConfig initialized) {
        moduleConfig = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    final ConfigService service = getManager().find(CoreModule.NAME).provider().getService(ConfigService.class);
    this.spanForward = new SpanForward(((ZipkinConfigService)service).toZipkinReceiverConfig(), getManager());
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    try {
      new ZipkinRabbitMQHandler(moduleConfig, spanForward, getManager()).start();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String[] requiredModules() {
    return new String[] {
        CoreModule.NAME,
    };
  }
}
