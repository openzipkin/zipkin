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
package zipkin.server;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import zipkin2.server.internal.EnableZipkinServer;
import zipkin2.server.internal.ZipkinActuatorImporter;
import zipkin2.server.internal.ZipkinModuleImporter;
import zipkin2.server.internal.banner.ZipkinBanner;

/**
 * This adds the {@link EnableAutoConfiguration} annotation, but disables it by default to save
 * startup time.
 *
 * <p>Supported Zipkin modules like zipkin-gcp need to explicitly configure themselves.
 *
 * <p>For example, add the following to {@code src/main/resources/zipkin-server-stackdriver.yml}:
 * <pre>{@code
 * zipkin:
 *   internal:
 *     module:
 *       stackdriver: zipkin.module.storage.stackdriver.ZipkinStackdriverStorageModule
 * }</pre>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableZipkinServer
public class ZipkinServer {
  static {
    String log4j2ClassName = "org.apache.logging.log4j.jul.LogManager";
    try {
      Class.forName(log4j2ClassName);
      // Make sure java.util.logging goes to log4j2
      // https://docs.spring.io/spring-boot/docs/current/reference/html/howto-logging.html#howto-configure-log4j-for-logging
      System.setProperty("java.util.logging.manager", log4j2ClassName);
    } catch (Exception e) {
      // using SLF4J impl
      SLF4JBridgeHandler.removeHandlersForRootLogger();
      SLF4JBridgeHandler.install();
    }
  }

  public static void main(String[] args) {
    new SpringApplicationBuilder(ZipkinServer.class)
      .banner(new ZipkinBanner())
      .initializers(new ZipkinModuleImporter(), new ZipkinActuatorImporter())
      .properties(
        EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY + "=false",
        "spring.config.name=zipkin-server").run(args);
  }
}
