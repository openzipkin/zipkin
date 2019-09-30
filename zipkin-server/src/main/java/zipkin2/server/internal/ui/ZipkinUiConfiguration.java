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
package zipkin2.server.internal.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ServerCacheControl;
import com.linecorp.armeria.common.ServerCacheControlBuilder;
import com.linecorp.armeria.server.AbstractHttpService;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.RedirectService;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.file.HttpFileBuilder;
import com.linecorp.armeria.server.file.HttpFileServiceBuilder;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;

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
@Configuration
@EnableConfigurationProperties({ZipkinUiProperties.class, CompressionProperties.class})
@ConditionalOnProperty(name = "zipkin.ui.enabled", matchIfMissing = true)
public class ZipkinUiConfiguration {
  @Autowired
  ZipkinUiProperties ui;

  @Value("classpath:zipkin-ui/index.html")
  Resource indexHtml;
  @Value("classpath:zipkin-lens/index.html")
  Resource lensIndexHtml;

  @Bean @Lazy String processedIndexHtml() throws IOException {
    return processedIndexHtml(indexHtml);
  }

  @Bean @Lazy String processedLensIndexHtml() throws IOException {
    return processedIndexHtml(lensIndexHtml);
  }

  String processedIndexHtml(Resource indexHtml) throws IOException {
    String baseTagValue = "/".equals(ui.getBasepath()) ? "/" : ui.getBasepath() + "/";
    char[] buffer = new char[1024];
    StringBuilder builder = new StringBuilder();
    try (InputStream fromClasspath = indexHtml.getInputStream();
         InputStreamReader reader = new InputStreamReader(fromClasspath, UTF_8)) {
      while (true) {
        int read = reader.read(buffer, 0, buffer.length);
        if (read < 0) break;
        builder.append(buffer, 0, read);
      }
    }
    String beforeReplacement = builder.toString();
    return beforeReplacement.replaceAll(
      "base href=\"[^\"]+\"", "base href=\"" + baseTagValue + "\""
    );
  }

  @Bean @Lazy IndexSwitchingService indexSwitchingService() throws IOException {
    final HttpFileBuilder legacyIndex;
    final HttpFileBuilder lensIndex;
    if (DEFAULT_BASEPATH.equals(ui.getBasepath())) {
      legacyIndex = HttpFileBuilder.ofResource("zipkin-ui/index.html");
      lensIndex = HttpFileBuilder.ofResource("zipkin-lens/index.html");
    } else {
      legacyIndex = HttpFileBuilder.of(HttpData.wrap(processedIndexHtml().getBytes(UTF_8)));
      lensIndex = HttpFileBuilder.of(HttpData.wrap(processedLensIndexHtml().getBytes(UTF_8)));
    }

    ServerCacheControl maxAgeMinute = new ServerCacheControlBuilder().maxAgeSeconds(60).build();
    legacyIndex.contentType(MediaType.HTML_UTF_8).cacheControl(maxAgeMinute);
    lensIndex.contentType(MediaType.HTML_UTF_8).cacheControl(maxAgeMinute);

    // In both our old and new UI, assets have hashes in the filenames (generated by webpack).
    // This allows us to host both simultaneously without conflict as long as we change the index
    // file to point to the correct files.
    return new IndexSwitchingService(
      legacyIndex.build().asService(), lensIndex.build().asService());
  }

  @Bean @Lazy ArmeriaServerConfigurator uiServerConfigurator(
    IndexSwitchingService indexSwitchingService) throws IOException {
    HttpService indexService =
      indexHtml.isReadable() ? indexSwitchingService : indexSwitchingService.lensIndex;

    ServerCacheControl maxAgeYear =
      new ServerCacheControlBuilder().maxAgeSeconds(TimeUnit.DAYS.toSeconds(365)).build();
    Service<HttpRequest, HttpResponse> uiFileService =
      HttpFileServiceBuilder.forClassPath("zipkin-ui").cacheControl(maxAgeYear).build()
        .orElse(
          HttpFileServiceBuilder.forClassPath("zipkin-lens").cacheControl(maxAgeYear).build());

    byte[] config = new ObjectMapper().writeValueAsBytes(ui);
    return sb -> {
      sb.service("/zipkin/config.json", HttpFileBuilder.of(HttpData.wrap(config))
        .cacheControl(new ServerCacheControlBuilder().maxAgeSeconds(600).build())
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
    };
  }

  @Bean Consumer<MeterRegistry.Config> noFaviconMetrics() {
    return config -> config.meterFilter(MeterFilter.deny(id -> {
      String uri = id.getTag("uri");
      return uri != null && uri.startsWith("/favicon.ico");
    }));
  }

  static class IndexSwitchingService extends AbstractHttpService {
    final HttpService legacyIndex;
    final HttpService lensIndex;

    IndexSwitchingService(HttpService legacyIndex, HttpService lensIndex) {
      this.legacyIndex = legacyIndex;
      this.lensIndex = lensIndex;
    }

    @Override
    public HttpResponse doGet(ServiceRequestContext ctx, HttpRequest req)
      throws Exception {
      Set<Cookie> cookies = ServerCookieDecoder.LAX.decode(
        req.headers().get(HttpHeaderNames.COOKIE, ""));
      for (Cookie cookie : cookies) {
        if (cookie.name().equals("lens") && Boolean.parseBoolean(cookie.value())) {
          return lensIndex.serve(ctx, req);
        }
      }
      return legacyIndex.serve(ctx, req);
    }
  }
}
