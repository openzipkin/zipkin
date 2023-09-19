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

package zipkin.server.receiver.zipkin.kafka;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.zipkin.kafka.KafkaHandler;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import zipkin.server.core.services.ZipkinConfigService;

public class ZipkinKafkaReceiverProvider extends ModuleProvider {
  private ZipkinKafkaReceiverConfig moduleConfig;
  private SpanForward spanForward;
  private KafkaHandler kafkaHandler;

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinKafkaReceiverModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<ZipkinKafkaReceiverConfig>() {
      @Override
      public Class<ZipkinKafkaReceiverConfig> type() {
        return ZipkinKafkaReceiverConfig.class;
      }

      @Override
      public void onInitialized(ZipkinKafkaReceiverConfig initialized) {
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
    kafkaHandler = new KafkaHandler(moduleConfig.toSkyWalkingConfig(), this.spanForward, getManager());
    kafkaHandler.start();
  }

  @Override
  public String[] requiredModules() {
    return new String[] {
        CoreModule.NAME,
    };
  }
}
