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

package zipkin.server.query.http;

import com.linecorp.armeria.common.HttpMethod;
import org.apache.skywalking.oap.query.zipkin.ZipkinQueryModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;

import java.util.Collections;

public class HTTPQueryProvider extends ModuleProvider {
  private HTTPQueryConfig moduleConfig;
  private HTTPServer httpServer;
  @Override
  public String name() {
    return "zipkin";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return ZipkinQueryModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<HTTPQueryConfig>() {
      @Override
      public Class<HTTPQueryConfig> type() {
        return HTTPQueryConfig.class;
      }

      @Override
      public void onInitialized(HTTPQueryConfig initialized) {
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
      httpServer = new HTTPServer(httpServerConfig);
      httpServer.initialize();
    }
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    if (httpServer != null) {
      httpServer.addHandler(new HTTPQueryHandler(moduleConfig, getManager()),
          Collections.singletonList(HttpMethod.GET));
    } else {
      getManager().find(CoreModule.NAME).provider()
          .getService(HTTPHandlerRegister.class).addHandler(
              new HTTPQueryHandler(moduleConfig, getManager()),
              Collections.singletonList(HttpMethod.GET)
          );
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
}
