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

package zipkin.server.receiver.zipkin.http;

import com.linecorp.armeria.common.HttpMethod;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;
import org.apache.skywalking.oap.server.receiver.zipkin.handler.ZipkinSpanHTTPHandler;

import java.util.Arrays;

public class ZipkinHTTPReceiverProvider extends ModuleProvider {
  private ZipkinHTTPReceiverConfig moduleConfig;
  private ZipkinSpanHTTPHandler httpHandler;

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinHTTPReceiverModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<ZipkinHTTPReceiverConfig>() {
      @Override
      public Class<ZipkinHTTPReceiverConfig> type() {
        return ZipkinHTTPReceiverConfig.class;
      }

      @Override
      public void onInitialized(ZipkinHTTPReceiverConfig initialized) {
        moduleConfig = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    final SpanForwardService spanForward = getManager().find(ZipkinReceiverModule.NAME).provider().getService(SpanForwardService.class);
    httpHandler = new ZipkinSpanHTTPHandler(spanForward, getManager());

    final HTTPHandlerRegister httpRegister = getManager().find(CoreModule.NAME).provider().getService(HTTPHandlerRegister.class);
    httpRegister.addHandler(httpHandler, Arrays.asList(HttpMethod.POST, HttpMethod.GET));
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
  }

  @Override
  public String[] requiredModules() {
    return new String[] {
        CoreModule.NAME,
    };
  }
}
