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

package zipkin.server.receiver.otlp;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverConfig;
import org.apache.skywalking.oap.server.receiver.otel.OtelMetricReceiverModule;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryMetricRequestProcessor;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryTraceHandler;
import zipkin.server.receiver.otlp.handler.OTLPTraceHandler;

public class OTLPTraceProvider extends ModuleProvider {
  private OTLPTraceConfig moduleConfig;
  private OpenTelemetryTraceHandler traceHandler;

  @Override
  public String name() {
    return "zipkin";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return OtelMetricReceiverModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<OTLPTraceConfig>() {

      @Override
      public Class<OTLPTraceConfig> type() {
        return OTLPTraceConfig.class;
      }

      @Override
      public void onInitialized(OTLPTraceConfig initialized) {
        moduleConfig = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    this.registerServiceImplementation(OpenTelemetryMetricRequestProcessor.class,
        new OpenTelemetryMetricRequestProcessor(getManager(), new OtelMetricReceiverConfig()));
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    GRPCHandlerRegister handlerRegister = getManager().find(CoreModule.NAME).provider().getService(GRPCHandlerRegister.class);
    traceHandler = new OTLPTraceHandler(handlerRegister, getManager());
    traceHandler.active();
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
  }

  @Override
  public String[] requiredModules() {
    return new String[0];
  }
}
