/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.server.internal.ui;

import com.fasterxml.jackson.core.JsonGenerator;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ServerCacheControl;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.RedirectService;
import com.linecorp.armeria.server.file.FileService;
import com.linecorp.armeria.server.file.HttpFile;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import zipkin2.server.internal.JsonUtil;

import static java.nio.charset.StandardCharsets.UTF_8;
import static zipkin2.server.internal.ui.ZipkinUiProperties.DEFAULT_BASEPATH;

/**
 * Zipkin-UI is a single-page application mounted at /zipkin. For simplicity, assume paths mentioned
 * below are relative to that. For example, the UI reads config.json, from the absolute path
 * /zipkin/config.json
 *
 * <p>When looking at a trace, the browser is sent to the path "/traces/{id}". For the single-page
 * app to serve that route, the server needs to forward the request to "/index.html". The same
 * forwarding applies to "/dependencies" and any other routes the UI controls.
 *
 * <p>Under the scenes the JavaScript code looks at {@code window.location} to figure out what the
 * UI should do. This is handled by a route api defined in the crossroads library.
 *
 * <h3>Caching</h3>
 * <p>This includes a hard-coded cache policy, consistent with zipkin-scala.
 * <ul>
 *   <li>1 minute for index.html</li>
 *   <li>10 minute for /config.json</li>
 *   <li>365 days for hashed resources (ex /app-e12b3bbb7e5a572f270d.min.js)</li>
 * </ul>
 * Since index.html links to hashed resource names, any change to it will orphan old resources.
 * That's why hashed resource age can be 365 days.
 */
@EnableConfigurationProperties({ZipkinUiProperties.class, CompressionProperties.class})
@ConditionalOnProperty(name = "zipkin.ui.enabled", matchIfMissing = true)
public class ZipkinUiConfiguration {
  @Autowired ZipkinUiProperties ui;
  @Value("classpath:zipkin-lens/index.html") Resource lensIndexHtml;

  @Bean
  HttpService indexService() throws Exception {
    HttpService lensIndex = maybeIndexService(ui.getBasepath(), lensIndexHtml);
    if (lensIndex != null) return lensIndex;
    throw new BeanCreationException("Could not load Lens UI from " + lensIndexHtml);
  }

  @Bean ArmeriaServerConfigurator uiServerConfigurator(
    HttpService indexService,
    Optional<MeterRegistry> meterRegistry
  ) throws IOException {
    ServerCacheControl maxAgeYear =
      ServerCacheControl.builder().maxAgeSeconds(TimeUnit.DAYS.toSeconds(365)).build();

    HttpService uiFileService = FileService.builder(getClass().getClassLoader(), "zipkin-lens")
      .cacheControl(maxAgeYear)
      .build();

    String config = writeConfig(ui);
    return sb -> {
      sb.service("/zipkin/config.json", HttpFile.builder(HttpData.ofUtf8(config))
        .cacheControl(ServerCacheControl.builder().maxAgeSeconds(600).build())
        .contentType(MediaType.JSON_UTF_8)
        .build()
        .asService());

      sb.serviceUnder("/zipkin/", uiFileService);

      // TODO This approach requires maintenance when new UI routes are added. Change to the following:
      // If the path is a a file w/an extension, treat normally.
      // Otherwise instead of returning 404, forward to the index.
      // See https://github.com/twitter/finatra/blob/458c6b639c3afb4e29873d123125eeeb2b02e2cd/http/src/main/scala/com/twitter/finatra/http/response/ResponseBuilder.scala#L321
      sb.service("/zipkin/", indexService)
        .service("/zipkin/index.html", indexService)
        .service("/zipkin/traces/{id}", indexService)
        .service("/zipkin/dependency", indexService)
        .service("/zipkin/traceViewer", indexService);

      sb.service("/favicon.ico", new RedirectService(HttpStatus.FOUND, "/zipkin/favicon.ico"))
        .service("/", new RedirectService(HttpStatus.FOUND, "/zipkin/"))
        .service("/zipkin", new RedirectService(HttpStatus.FOUND, "/zipkin/"));

      // don't add metrics for favicon
      meterRegistry.ifPresent(m -> m.config().meterFilter(MeterFilter.deny(id -> {
        String uri = id.getTag("uri");
        return uri != null && uri.startsWith("/favicon.ico");
      })));
    };
  }

  //
  // environment: '',
  // queryLimit: 10,
  // defaultLookback: 15 * 60 * 1000, // 15 minutes
  // searchEnabled: true,
  // dependency: {
  //   enabled: true,
  //   lowErrorRate: 0.5, // 50% of calls in error turns line yellow
  //   highErrorRate: 0.75 // 75% of calls in error turns line red
  // }
  static String writeConfig(ZipkinUiProperties ui) throws IOException {
    StringWriter writer = new StringWriter();
    try (JsonGenerator generator = JsonUtil.createGenerator(writer)) {
      generator.useDefaultPrettyPrinter();
      generator.writeStartObject();
      generator.writeStringField("environment", ui.getEnvironment());
      generator.writeNumberField("queryLimit", ui.getQueryLimit());
      generator.writeNumberField("defaultLookback", ui.getDefaultLookback());
      generator.writeBooleanField("searchEnabled", ui.isSearchEnabled());
      generator.writeStringField("logsUrl", ui.getLogsUrl());
      generator.writeStringField("supportUrl", ui.getSupportUrl());
      generator.writeStringField("archivePostUrl", ui.getArchivePostUrl());
      generator.writeStringField("archiveUrl", ui.getArchiveUrl());
      generator.writeObjectFieldStart("dependency");
      generator.writeBooleanField("enabled", ui.getDependency().isEnabled());
      generator.writeNumberField("lowErrorRate", ui.getDependency().getLowErrorRate());
      generator.writeNumberField("highErrorRate", ui.getDependency().getHighErrorRate());
      generator.writeEndObject(); // .dependency
      generator.writeEndObject(); // .
    }
    return writer.toString();
  }

  static HttpService maybeIndexService(String basePath, Resource resource) throws IOException {
    String maybeContent = maybeResource(basePath, resource);
    if (maybeContent == null) return null;

    ServerCacheControl maxAgeMinute = ServerCacheControl.builder().maxAgeSeconds(60).build();

    return HttpFile.builder(HttpData.ofUtf8(maybeContent))
      .contentType(MediaType.HTML_UTF_8).cacheControl(maxAgeMinute)
      .build().asService();
  }

  static String maybeResource(String basePath, Resource resource) throws IOException {
    if (!resource.isReadable()) return null;

    try (InputStream stream = resource.getInputStream()) {
      String content = StreamUtils.copyToString(stream, UTF_8);
      if (DEFAULT_BASEPATH.equals(basePath)) return content;

      String baseTagValue = "/".equals(basePath) ? "/" : basePath + "/";
      // html-webpack-plugin seems to strip out quotes from the base tag when compiling so be
      // careful with this matcher.
      return content.replaceAll(
        "<base href=[^>]+>", "<base href=\"" + baseTagValue + "\">"
      );
    }
  }
}
