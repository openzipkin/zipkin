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

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ServerCacheControl;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.RedirectService;
import com.linecorp.armeria.server.cors.CorsService;
import com.linecorp.armeria.server.cors.CorsServiceBuilder;
import com.linecorp.armeria.server.file.FileService;
import com.linecorp.armeria.server.file.HttpFile;
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
import zipkin.server.core.services.HTTPConfigurableServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HTTPQueryProvider extends ModuleProvider {
  static final String DEFAULT_UI_BASEPATH = "/zipkin";

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
      httpServer = new HTTPConfigurableServer(httpServerConfig);
      httpServer.initialize();
    }
  }

  @Override
  public void start() throws ServiceNotProvidedException, ModuleStartException {
    HTTPConfigurableServer.ServerConfiguration corsConfiguration = (server) -> {
      CorsServiceBuilder corsBuilder = CorsService.builder(moduleConfig.getAllowedOrigins().split(","))
          // NOTE: The property says query, and the UI does not use POST, but we allow POST?
          //
          // The reason is that our former CORS implementation accidentally allowed POST. People doing
          // browser-based tracing relied on this, so we can't remove it by default. In the future, we
          // could split the collector's CORS policy into a different property, still allowing POST
          // with content-type by default.
          .allowRequestMethods(HttpMethod.GET, HttpMethod.POST)
          .allowRequestHeaders(HttpHeaderNames.CONTENT_TYPE,
              // Use literals to avoid a runtime dependency on armeria-grpc types
              HttpHeaderNames.of("X-GRPC-WEB"))
          .exposeHeaders("grpc-status", "grpc-message", "armeria.grpc.ThrowableProto-bin");
      server.decorator(corsBuilder::build);
    };

    final HTTPQueryHandler httpService = new HTTPQueryHandler(moduleConfig, getManager());
    if (httpServer != null) {
      httpServer.addHandler(httpService, Collections.singletonList(HttpMethod.GET));
      httpServer.addHandler(corsConfiguration, Arrays.asList(HttpMethod.GET, HttpMethod.POST));

      if (moduleConfig.getUiEnable()) {
        loadUIServices((service, methods) -> httpServer.addHandler(service, methods), httpService);
      }
      return;
    }

    final HTTPHandlerRegister httpRegister = getManager().find(CoreModule.NAME).provider()
        .getService(HTTPHandlerRegister.class);
    httpRegister.addHandler(httpService, Collections.singletonList(HttpMethod.GET));
    httpRegister.addHandler(corsConfiguration, Arrays.asList(HttpMethod.GET, HttpMethod.POST));

    if (moduleConfig.getUiEnable()) {
      loadUIServices(httpRegister, httpService);
    }
  }

  private void loadUIServices(HTTPHandlerRegister httpRegister, HTTPQueryHandler httpService) {
    HttpService lensIndex;
    HttpService uiFileService;

    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    URL indexPage = contextClassLoader.getResource("zipkin-lens/index.html");
    if (indexPage == null) {
      throw new IllegalStateException("Cannot find ui pages");
    }
    final String uiBasePath = moduleConfig.getUiBasePath();
    try {
      lensIndex = maybeIndexService(uiBasePath, indexPage);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load ui", e);
    }

    ServerCacheControl maxAgeYear =
        ServerCacheControl.builder().maxAgeSeconds(TimeUnit.DAYS.toSeconds(365)).build();
    uiFileService = FileService.builder(contextClassLoader, "zipkin-lens")
        .cacheControl(maxAgeYear)
        .build();

    httpRegister.addHandler((HTTPConfigurableServer.ServerConfiguration) builder -> {
      builder.annotatedService().pathPrefix(uiBasePath + "/").build(httpService);
      builder.serviceUnder(uiBasePath + "/", uiFileService);

      builder.service(uiBasePath+ "/", lensIndex)
          .service(uiBasePath + "/index.html", lensIndex)
          .service(uiBasePath + "/traces/{id}", lensIndex)
          .service(uiBasePath + "/dependency", lensIndex)
          .service(uiBasePath + "/traceViewer", lensIndex);

      builder.service("/favicon.ico", new RedirectService(HttpStatus.FOUND, uiBasePath + "/favicon.ico"))
          .service("/", new RedirectService(HttpStatus.FOUND, uiBasePath + "/"))
          .service(uiBasePath, new RedirectService(HttpStatus.FOUND, uiBasePath + "/"));
    }, Collections.singletonList(HttpMethod.GET));
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

  static HttpService maybeIndexService(String basePath, URL resource) throws IOException {
    String maybeContent = maybeResource(basePath, resource);
    if (maybeContent == null) return null;

    ServerCacheControl maxAgeMinute = ServerCacheControl.builder().maxAgeSeconds(60).build();

    return HttpFile.builder(HttpData.ofUtf8(maybeContent))
        .contentType(MediaType.HTML_UTF_8).cacheControl(maxAgeMinute)
        .build().asService();
  }

  static String maybeResource(String basePath, URL resource) throws IOException {
    String content = copyToString(resource, UTF_8);
    if (DEFAULT_UI_BASEPATH.equals(basePath)) return content;

    String baseTagValue = "/".equals(basePath) ? "/" : basePath + "/";
    // html-webpack-plugin seems to strip out quotes from the base tag when compiling so be
    // careful with this matcher.
    return content.replaceAll(
        "<base href=[^>]+>", "<base href=\"" + baseTagValue + "\">"
    );
  }

  static String copyToString(URL in, Charset charset) throws IOException {
    StringBuilder out = new StringBuilder(4096);
    try (InputStream input = in.openStream(); InputStreamReader reader = new InputStreamReader(input, charset)) {
      char[] buffer = new char[4096];

      int charsRead;
      while((charsRead = reader.read(buffer)) != -1) {
        out.append(buffer, 0, charsRead);
      }
    }

    return out.toString();
  }

}
