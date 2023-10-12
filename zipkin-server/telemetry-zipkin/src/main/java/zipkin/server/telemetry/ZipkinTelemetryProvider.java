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

package zipkin.server.telemetry;

import com.linecorp.armeria.common.HttpMethod;
import io.prometheus.client.hotspot.DefaultExports;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.prometheus.PrometheusMetricsCollector;
import org.apache.skywalking.oap.server.telemetry.prometheus.PrometheusMetricsCreator;
import zipkin.server.core.services.HTTPConfigurableServer;

import java.util.Arrays;

public class ZipkinTelemetryProvider extends ModuleProvider {
  private ZipkinTelemetryConfig moduleConfig;
  private HTTPServer httpServer;
  @Override
  public String name() {
    return "zipkin";
  }

  @Override
  public Class<? extends ModuleDefine> module() {
    return TelemetryModule.class;
  }

  @Override
  public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
    return new ConfigCreator<ZipkinTelemetryConfig>() {
      @Override
      public Class<ZipkinTelemetryConfig> type() {
        return ZipkinTelemetryConfig.class;
      }

      @Override
      public void onInitialized(ZipkinTelemetryConfig initialized) {
        moduleConfig = initialized;
      }
    };
  }

  @Override
  public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    this.registerServiceImplementation(MetricsCreator.class, new PrometheusMetricsCreator());
    this.registerServiceImplementation(MetricsCollector.class, new PrometheusMetricsCollector());

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

    DefaultExports.initialize();
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    final ZipkinTelemetryHandler handler = new ZipkinTelemetryHandler();
    if (httpServer != null) {
      httpServer.addHandler(handler, Arrays.asList(HttpMethod.GET, HttpMethod.POST));
      return;
    }
    final HTTPHandlerRegister httpRegister = getManager().find(CoreModule.NAME).provider()
        .getService(HTTPHandlerRegister.class);
    httpRegister.addHandler(handler, Arrays.asList(HttpMethod.GET, HttpMethod.POST));
  }

  @Override
  public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    if (httpServer != null) {
      httpServer.start();
    }
  }

  @Override
  public String[] requiredModules() {
    return new String[0];
  }
}
