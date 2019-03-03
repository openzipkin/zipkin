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
package zipkin2.autoconfigure.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.RedirectService;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.file.HttpFile;
import com.linecorp.armeria.server.file.HttpFileBuilder;
import com.linecorp.armeria.server.file.HttpFileService;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;

import static com.linecorp.armeria.common.HttpHeaderNames.CACHE_CONTROL;
import static com.linecorp.armeria.common.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static zipkin2.autoconfigure.ui.ZipkinUiProperties.DEFAULT_BASEPATH;

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
@EnableConfigurationProperties(ZipkinUiProperties.class)
@ConditionalOnProperty(name = "zipkin.ui.enabled", matchIfMissing = true)
class ZipkinUiAutoConfiguration {
  static final HttpHeaders CACHE_YEAR =
    HttpHeaders.of(CACHE_CONTROL, "max-age=" + TimeUnit.DAYS.toSeconds(365));
  static final HttpHeaders CONFIG_HEADERS = HttpHeaders.of(HttpStatus.OK)
    .add(HttpHeaders.of(CONTENT_TYPE, "application/json"))
    .add(HttpHeaders.of(CACHE_CONTROL, "max-age=" + TimeUnit.MINUTES.toSeconds(10)));
  static final HttpHeaders INDEX_HEADERS = HttpHeaders.of(HttpStatus.OK)
    .add(HttpHeaders.of(CONTENT_TYPE, "text/html"))
    .add(HttpHeaders.of(CACHE_CONTROL, "max-age=" + TimeUnit.MINUTES.toSeconds(1)));

  @Autowired
  ZipkinUiProperties ui;

  @Value("classpath:${zipkin.ui.resource-path:zipkin-ui}/index.html")
  Resource indexHtml;

  @Bean
  @Lazy
  String processedIndexHtml() throws IOException {
    String baseTagValue = "/".equals(ui.getBasepath()) ? "/" : ui.getBasepath() + "/";
    Document soup;
    try (InputStream is = indexHtml.getInputStream()) {
      soup = Jsoup.parse(is, null, baseTagValue);
    }
    if (soup.head().getElementsByTag("base").isEmpty()) {
      soup.head().appendChild(
        soup.createElement("base")
      );
    }
    soup.head().getElementsByTag("base").attr("href", baseTagValue);
    return soup.html();
  }

  @Bean ArmeriaServerConfigurator uiServerConfigurator(
    @Value("${zipkin.ui.resource-path:zipkin-ui}") String resourcePath) throws IOException {
    Service<HttpRequest, HttpResponse> uiFileService =
      new AddHttpHeadersService(HttpFileService.forClassPath(resourcePath), CACHE_YEAR);

    byte[] index;
    if (DEFAULT_BASEPATH.equals(ui.getBasepath())) {
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(indexHtml.getInputStream(), UTF_8))) {
        index = reader.lines().collect(Collectors.joining("\n")).getBytes(UTF_8);
      }
    } else {
      index = processedIndexHtml().getBytes(UTF_8);
    }

    byte[] config = new ObjectMapper().writeValueAsBytes(ui);

    HttpFile indexPage = HttpFileBuilder.of(HttpData.of(index)).addHeaders(INDEX_HEADERS).build();

    return sb -> sb
      .service("/zipkin/config.json",
        HttpFileBuilder.of(HttpData.of(config)).addHeaders(CONFIG_HEADERS).build().asService())
      .service("/zipkin/index.html", indexPage.asService())

      // TODO This approach requires maintenance when new UI routes are added. Change to the following:
      // If the path is a a file w/an extension, treat normally.
      // Otherwise instead of returning 404, forward to the index.
      // See https://github.com/twitter/finatra/blob/458c6b639c3afb4e29873d123125eeeb2b02e2cd/http/src/main/scala/com/twitter/finatra/http/response/ResponseBuilder.scala#L321
      .service("/zipkin/", indexPage.asService())
      .service("/zipkin/traces/{id}", indexPage.asService())
      .service("/zipkin/dependency", indexPage.asService())
      .service("/zipkin/traceViewer", indexPage.asService())

      .serviceUnder("/zipkin/", uiFileService)
      .service("/favicon.ico", new RedirectService(HttpStatus.FOUND, "/zipkin/favicon.ico"))
      .service("/", new RedirectService(HttpStatus.FOUND, "/zipkin/"));
  }
}
