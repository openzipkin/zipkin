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
package zipkin2.server.internal;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.AggregatedHttpMessage;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.Server;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This code ensures you can setup SSL.
 *
 * <p>This is inspired by com.linecorp.armeria.spring.ArmeriaSslConfigurationTest
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "spring.config.name=zipkin-server",
    "armeria.ssl.enabled=true",
    "armeria.ports[1].port=0",
    "armeria.ports[1].protocols[0]=https",
    // redundant in zipkin-server-shared https://github.com/spring-projects/spring-boot/issues/16394
    "armeria.ports[0].port=${server.port}",
    "armeria.ports[0].protocols[0]=http",
  })
@RunWith(SpringRunner.class)
public class ITZipkinServerSsl {
  @Autowired Server server;

  // We typically use OkHttp in our tests, but Armeria bundles a handy insecure trust manager
  final ClientFactory clientFactory = new ClientFactoryBuilder()
    .sslContextCustomizer(b -> b.trustManager(InsecureTrustManagerFactory.INSTANCE))
    .build();

  @Test public void callHealthEndpoint_HTTP() {
    callHealthEndpoint(SessionProtocol.HTTP);
  }

  @Test public void callHealthEndpoint_HTTPS() {
    callHealthEndpoint(SessionProtocol.HTTPS);
  }

  void callHealthEndpoint(SessionProtocol http) {
    AggregatedHttpMessage response = HttpClient.of(clientFactory, baseUrl(server, http))
      .get("/health")
      .aggregate().join();

    assertThat(response.status()).isEqualTo(HttpStatus.OK);
  }

  static String baseUrl(Server server, SessionProtocol protocol) {
    return server.activePorts().values().stream()
      .filter(p -> p.hasProtocol(protocol)).findAny()
      .map(p -> protocol.uriText() + "://127.0.0.1:" + p.localAddress().getPort())
      .orElseThrow(() -> new AssertionError(protocol + " port not open"));
  }
}
