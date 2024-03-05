/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.spring.ArmeriaSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.elasticsearch.Access.configureSsl;

/**
 * This code ensures you can setup SSL. Look at {@link ArmeriaSettings} for property names.
 *
 * <p>This is inspired by com.linecorp.armeria.spring.ArmeriaSslConfigurationTest
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    // TODO: use normal spring.server properties after https://github.com/line/armeria/issues/1834
    "armeria.ssl.enabled=true",
    "armeria.ssl.key-store=classpath:keystore.p12",
    "armeria.ssl.key-store-password=password",
    "armeria.ssl.key-store-type=PKCS12",
    "armeria.ssl.trust-store=classpath:keystore.p12",
    "armeria.ssl.trust-store-password=password",
    "armeria.ssl.trust-store-type=PKCS12",
    "armeria.ports[1].port=0",
    "armeria.ports[1].protocols[0]=https",
    // redundant in zipkin-server-shared https://github.com/spring-projects/spring-boot/issues/16394
    "armeria.ports[0].port=${server.port}",
    "armeria.ports[0].protocols[0]=http",
  })
class ITZipkinServerSsl {
  @Autowired Server server;
  @Autowired ArmeriaSettings armeriaSettings;

  ClientFactory clientFactory;

  @BeforeEach void configureClientFactory() {
    clientFactory = configureSsl(ClientFactory.builder(), armeriaSettings.getSsl()).build();
  }

  @Test void callHealthEndpoint_HTTP() {
    callHealthEndpoint(SessionProtocol.HTTP);
  }

  @Test void callHealthEndpoint_HTTPS() {
    callHealthEndpoint(SessionProtocol.HTTPS);
  }

  void callHealthEndpoint(SessionProtocol http) {
    AggregatedHttpResponse response =
      WebClient.builder(baseUrl(server, http)).factory(clientFactory).build()
        .get("/health")
        .aggregate().join();

    assertThat(response.status()).isEqualTo(HttpStatus.OK);
  }

  static String baseUrl(Server server, SessionProtocol protocol) {
    return server.activePorts().values().stream()
      .filter(p -> p.hasProtocol(protocol)).findAny()
      .map(p -> protocol.uriText() + "://localhost:" + p.localAddress().getPort())
      .orElseThrow(() -> new AssertionError(protocol + " port not open"));
  }
}
