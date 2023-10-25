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
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.handler.ZipkinSpanHTTPHandler;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import zipkin.server.core.services.HTTPConfigurableServer;
import zipkin.server.core.services.ZipkinConfigService;

import java.util.Arrays;

public class ZipkinHTTPReceiverProvider extends ModuleProvider {
  private ZipkinHTTPReceiverConfig moduleConfig;
  private ZipkinSpanHTTPHandler httpHandler;
  private HTTPServer httpServer;

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
    if (moduleConfig.getRestPort() > 0) {
      HTTPServerConfig httpServerConfig = HTTPServerConfig.builder()
          .host(moduleConfig.getRestHost())
          .port(moduleConfig.getRestPort())
          .contextPath(moduleConfig.getRestContextPath())
          .idleTimeOut(moduleConfig.getRestIdleTimeOut())
          .maxThreads(moduleConfig.getRestMaxThreads())
          .acceptQueueSize(moduleConfig.getRestAcceptQueueSize())
          .maxRequestHeaderSize(moduleConfig.getRestMaxRequestHeaderSize())
          .build();
      httpServer = new HTTPConfigurableServer(httpServerConfig);
      httpServer.initialize();
    }
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    final ConfigService service = getManager().find(CoreModule.NAME).provider().getService(ConfigService.class);
    final SpanForward spanForward = new SpanForward(((ZipkinConfigService)service).toZipkinReceiverConfig(), getManager());
    httpHandler = new ZipkinSpanHTTPHandler(spanForward, getManager());

    if (httpServer != null) {
      httpServer.addHandler(httpHandler, Arrays.asList(HttpMethod.POST, HttpMethod.GET));
    } else {
      final HTTPHandlerRegister httpRegister = getManager().find(CoreModule.NAME).provider().getService(HTTPHandlerRegister.class);
      httpRegister.addHandler(httpHandler, Arrays.asList(HttpMethod.POST, HttpMethod.GET));
    }
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    if (httpServer != null) {
      httpServer.start();
    }
  }

  @Override
  public String[] requiredModules() {
    return new String[] {
        CoreModule.NAME,
    };
  }

  public ZipkinSpanHTTPHandler getHttpHandler() {
    return httpHandler;
  }
}
