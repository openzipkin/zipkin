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
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import zipkin2.server.internal.EnableZipkinServer;
import zipkin2.server.internal.banner.ZipkinBanner;

@SpringBootApplication
@EnableZipkinServer
public class ZipkinServer {

  static {
    // ensures jul-to-slf4j works
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  public static void main(String[] args) {
    new SpringApplicationBuilder(ZipkinServer.class)
      .banner(new ZipkinBanner())
      .properties("spring.config.name=zipkin-server").run(args);
  }
}
