/*
 * Copyright 2015-2018 The OpenZipkin Authors
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

import com.linecorp.armeria.server.tomcat.TomcatService;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@EnableAutoConfiguration
@EnableWebMvc
@Import(ZipkinUiAutoConfiguration.class)
public class TestServer {
  /**
   * Extracts a Tomcat {@link Connector} from Spring webapp context.
   */
  public static Connector getConnector(ServletWebServerApplicationContext applicationContext) {
    final TomcatWebServer container = (TomcatWebServer) applicationContext.getWebServer();

    // Start the container to make sure all connectors are available.
    container.start();
    return container.getTomcat().getConnector();
  }

  /**
   * Returns a new {@link TomcatService} that redirects the incoming requests to the Tomcat instance
   * provided by Spring Boot.
   */
  @Bean
  public TomcatService tomcatService(ServletWebServerApplicationContext applicationContext) {
    return TomcatService.forConnector(getConnector(applicationContext));
  }

  @Bean ArmeriaServerConfigurator httpCollectorConfigurator(TomcatService tomcatService) {
    return sb -> {
      sb.serviceUnder("/", tomcatService);
    };
  }
}
