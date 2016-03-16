/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Zipkin-UI is a single-page application that reads configuration from /config.json.
 *
 * <p>When looking at a trace, the browser is sent to the path "/traces/{id}". For the single-page
 * app to serve that route, the server needs to forward the request to "/index.html". The same
 * forwarding applies to "/dependencies" and any other routes the UI controls.
 *
 * <p>Under the scenes the JavaScript code looks at {@code window.location} to figure out what the
 * UI should do. This is handled by a route api defined in the crossroads library.
 */
@Configuration
@ConditionalOnResource(resources = "classpath:zipkin-ui") // from io.zipkin:zipkin-ui
public class ZipkinUiConfiguration extends WebMvcConfigurerAdapter {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**").addResourceLocations("classpath:/zipkin-ui/");
  }

  @RestController
  public static class ZipkinUi {

    @Autowired
    ZipkinServerProperties server;

    @RequestMapping(value = "/config.json", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
    public ZipkinServerProperties.Ui getUiConfig() {
      return server.getUi();
    }

    /**
     * This cherry-picks well-known routes the single-page app serves, and forwards to that as
     * opposed to returning a 404.
     */
    // TODO This approach requires maintenance when new UI routes are added. Change to the following:
    // If the path is a a file w/an extension, treat normally.
    // Otherwise instead of returning 404, forward to the index.
    // See https://github.com/twitter/finatra/blob/458c6b639c3afb4e29873d123125eeeb2b02e2cd/http/src/main/scala/com/twitter/finatra/http/response/ResponseBuilder.scala#L321
    @RequestMapping(value = {"/", "/traces/{id}", "/dependency"}, method = RequestMethod.GET)
    public ModelAndView forwardUiEndpoints(ModelMap model) {
      // Note: RequestMapping "/" requires us to use ModelAndView result vs just a string.
      // When "/" is mapped, the server literally returns "forward:/index.html" vs forwarding.
      return new ModelAndView("forward:/index.html", model);
    }
  }
}
