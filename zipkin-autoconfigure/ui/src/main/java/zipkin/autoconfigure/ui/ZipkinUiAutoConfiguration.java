/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.autoconfigure.ui;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE;

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
@RestController
public class ZipkinUiAutoConfiguration extends WebMvcConfigurerAdapter {

  @Autowired
  ZipkinUiProperties ui;

  @Value("classpath:zipkin-ui/index.html")
  Resource indexHtml;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/zipkin/**")
        .addResourceLocations("classpath:/zipkin-ui/")
        .setCachePeriod((int) TimeUnit.DAYS.toSeconds(365));
  }

  /**
   * This opts out of adding charset to png resources.
   *
   * <p>By default, {@linkplain CharacterEncodingFilter} adds a charset qualifier to all resources,
   * which helps, as javascript assets include extended character sets. However, the filter also
   * adds charset to well-known binary ones like png. This creates confusing content types, such as
   * "image/png;charset=UTF-8".
   *
   * See https://github.com/spring-projects/spring-boot/issues/5459
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public CharacterEncodingFilter characterEncodingFilter() {
    CharacterEncodingFilter filter = new CharacterEncodingFilter() {
      @Override
      protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().endsWith(".png");
      }
    };
    filter.setEncoding("UTF-8");
    filter.setForceEncoding(true);
    return filter;
  }

  @RequestMapping(value = "/zipkin/config.json", method = GET)
  public ResponseEntity<ZipkinUiProperties> serveUiConfig() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES))
        .contentType(MediaType.APPLICATION_JSON)
        .body(ui);
  }

  @RequestMapping(value = "/zipkin/index.html", method = GET)
  public ResponseEntity<Resource> serveIndex() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES))
        .body(indexHtml);
  }

  /**
   * This cherry-picks well-known routes the single-page app serves, and forwards to that as opposed
   * to returning a 404.
   */
  // TODO This approach requires maintenance when new UI routes are added. Change to the following:
  // If the path is a a file w/an extension, treat normally.
  // Otherwise instead of returning 404, forward to the index.
  // See https://github.com/twitter/finatra/blob/458c6b639c3afb4e29873d123125eeeb2b02e2cd/http/src/main/scala/com/twitter/finatra/http/response/ResponseBuilder.scala#L321
  @RequestMapping(value = {"/zipkin/", "/zipkin/traces/{id}", "/zipkin/dependency"}, method = GET)
  public ModelAndView forwardUiEndpoints() {
    return new ModelAndView("forward:/zipkin/index.html");
  }

  /** The UI looks for the api relative to where it is mounted, under /zipkin */
  @RequestMapping(value = "/zipkin/api/**", method = GET)
  public ModelAndView forwardApi(HttpServletRequest request) {
    String path = (String) request.getAttribute(PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    return new ModelAndView("forward:" + path.replaceFirst("/zipkin", ""));
  }

  /** Borrow favicon from UI assets under /zipkin */
  @RequestMapping(value = "/favicon.ico", method = GET)
  public ModelAndView favicon() {
    return new ModelAndView("forward:/zipkin/favicon.ico");
  }

  /** Make sure users who aren't familiar with /zipkin get to the right path */
  @RequestMapping(value = "/", method = GET)
  public void redirectRoot(HttpServletResponse response) throws IOException {
    // return 'Location: ./zipkin/' header (this wouldn't work with ModelAndView's 'redirect:./zipkin/')
    response.setHeader(HttpHeaders.LOCATION, "./zipkin/");
    response.setStatus(HttpStatus.FOUND.value());
  }
}
