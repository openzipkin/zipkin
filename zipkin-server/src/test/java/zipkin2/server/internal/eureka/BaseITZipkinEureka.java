/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.eureka;

import com.jayway.jsonpath.JsonPath;
import com.linecorp.armeria.server.Server;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import zipkin.server.ZipkinServer;

import static okhttp3.Credentials.basic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.utility.DockerImageName.parse;

/**
 * Integration test for Eureka, which validates with <a href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">Eureka REST operations</a>
 *
 * <p>Note: We only validate that authentication works, as it is cheaper than also testing without
 * it.
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    "zipkin.storage.type=", // cheat and test empty storage type
    "zipkin.collector.http.enabled=false",
    "zipkin.query.enabled=false",
    "zipkin.ui.enabled=false",
    "zipkin.discovery.eureka.hostname=localhost"
  })
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(OrderAnnotation.class) // so that deregistration tests don't flake the others.
abstract class BaseITZipkinEureka {
  /**
   * Path under the Eureka v2 endpoint for the app named "zipkin".
   * Note that Eureka always coerces app names to uppercase.
   */
  private static final String APPS_ZIPKIN = "apps/ZIPKIN";
  private final OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();

  @Autowired Server zipkin;

  final HttpUrl serviceUrl;

  BaseITZipkinEureka(HttpUrl serviceUrl) {
    this.serviceUrl = serviceUrl;
  }

  @Test @Order(1) void registersInEureka() throws IOException {
    // The zipkin server may start before Eureka processes the registration
    await().untilAsserted( // wait for registration
      () -> {
        try (Response response = getEurekaZipkinApp()) {
          assertThat(response.code()).isEqualTo(200);
        }
      });

    String json = getEurekaZipkinAppAsString();

    // Make sure the health status is OK
    assertThat(readString(json, "$.application.instance[0].status"))
      .isEqualTo("UP");

    int zipkinPort = zipkin.activePort().localAddress().getPort();

    // Note: Netflix/Eureka says use hostname, which can conflict on laptops.
    // Armeria adopts the spring-cloud-netflix convention shown here.
    assertThat(readString(json, "$.application.instance[0].instanceId"))
      .isEqualTo("localhost:zipkin:" + zipkinPort);

    // Make sure the vip address does not include the port!
    assertThat(readString(json, "$.application.instance[0].vipAddress"))
      .isEqualTo("localhost");

    // Make sure URLs are fully resolved. Notably, the status page URL defaults to the /info
    // endpoint, as that's the one chosen in spring-cloud-netflix.
    assertThat(readString(json, "$.application.instance[0].homePageUrl"))
      .isEqualTo("http://localhost:" + zipkinPort + "/zipkin");
    assertThat(readString(json, "$.application.instance[0].statusPageUrl"))
      .isEqualTo("http://localhost:" + zipkinPort + "/info");
    assertThat(readString(json, "$.application.instance[0].healthCheckUrl"))
      .isEqualTo("http://localhost:" + zipkinPort + "/health");
  }

  @Test @Order(2) void deregistersOnClose() {
    zipkin.close();
    await().untilAsserted( // wait for deregistration
      () -> {
        try (Response response = getEurekaZipkinApp()) {
          assertThat(response.code()).isEqualTo(404);
        }
      });
  }

  private String getEurekaZipkinAppAsString() throws IOException {
    try (Response response = getEurekaZipkinApp(); ResponseBody body = response.body()) {
      assertThat(response.isSuccessful()).withFailMessage(response.toString()).isTrue();
      return body != null ? body.string() : "";
    }
  }

  private Response getEurekaZipkinApp() throws IOException {
    HttpUrl url = serviceUrl.newBuilder().addEncodedPathSegments(APPS_ZIPKIN).build();
    Request.Builder rb = new Request.Builder().url(url)
      .header("Accept", "application/json"); // XML is default
    if (!url.username().isEmpty()) {
      rb.header("Authorization", basic(url.username(), url.password()));
    }
    return client.newCall(rb.build()).execute();
  }

  static final class EurekaContainer extends GenericContainer<EurekaContainer> {
    static final Logger LOGGER = LoggerFactory.getLogger(EurekaContainer.class);
    static final int EUREKA_PORT = 8761;

    EurekaContainer(Map<String, String> env) {
      super(parse("ghcr.io/openzipkin/zipkin-eureka:3.3.1"));
      withEnv(env);
      withExposedPorts(EUREKA_PORT);
      waitStrategy = Wait.forHealthcheck();
      withStartupTimeout(Duration.ofSeconds(60));
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }

    HttpUrl serviceUrl() {
      return new HttpUrl.Builder()
        .scheme("http")
        .host(getHost()).port(getMappedPort(EUREKA_PORT))
        .addEncodedPathSegments("eureka/v2").build();
    }
  }

  static String readString(String json, String jsonPath) {
    return JsonPath.compile(jsonPath).read(json);
  }
}
