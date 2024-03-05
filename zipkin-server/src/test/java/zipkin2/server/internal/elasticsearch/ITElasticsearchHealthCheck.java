/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.endpoint.EndpointSelectionTimeoutException;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.healthcheck.HealthCheckService;
import com.linecorp.armeria.server.healthcheck.SettableHealthChecker;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static zipkin2.server.internal.elasticsearch.TestResponses.GREEN_RESPONSE;
import static zipkin2.server.internal.elasticsearch.TestResponses.VERSION_RESPONSE;

/**
 * These tests focus on http client health checks not currently in zipkin-storage-elasticsearch.
 */
class ITElasticsearchHealthCheck {
  static final Logger logger = LoggerFactory.getLogger(ITElasticsearchHealthCheck.class.getName());
  // Health check interval is 100ms, but in-flight requests in CI might take a few hundred ms
  static final ConditionFactory awaitTimeout = await().timeout(1, TimeUnit.SECONDS);

  static final SettableHealthChecker server1Health = new SettableHealthChecker(true);

  @RegisterExtension
  static ServerExtension server1 = new ServerExtension() {
    @Override protected void configure(ServerBuilder sb) {
      sb.service("/", (ctx, req) -> sendResponseAfterAggregate(req, VERSION_RESPONSE));
      sb.service("/_cluster/health", HealthCheckService.of(server1Health));
      sb.serviceUnder("/_cluster/health/", (ctx, req) -> GREEN_RESPONSE.toHttpResponse());
    }
  };

  /** This ensures the response is sent after the request is fully read. */
  private static HttpResponse sendResponseAfterAggregate(HttpRequest req,
    AggregatedHttpResponse response) {
    final CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    req.aggregate().whenComplete((aggregatedReq, cause) -> {
      if (cause != null) {
        future.completeExceptionally(cause);
      } else {
        future.complete(response.toHttpResponse());
      }
    });
    return HttpResponse.from(future);
  }

  static final SettableHealthChecker server2Health = new SettableHealthChecker(true);

  @RegisterExtension
  static ServerExtension server2 = new ServerExtension() {
    @Override protected void configure(ServerBuilder sb) {
      sb.service("/", (ctx, req) -> sendResponseAfterAggregate(req, VERSION_RESPONSE));
      sb.service("/_cluster/health", HealthCheckService.of(server2Health));
      sb.serviceUnder("/_cluster/health/",
        (ctx, req) -> sendResponseAfterAggregate(req, GREEN_RESPONSE));
    }
  };

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @BeforeEach void setUp() {
    server1Health.setHealthy(true);
    server2Health.setHealthy(true);

    logger.info("server 1: {}, server 2: {}", server1.httpUri(), server2.httpUri());

    initWithHosts("127.0.0.1:" + server1.httpPort() + ",127.0.0.1:" + server2.httpPort());
  }

  private void initWithHosts(String hosts) {
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type=elasticsearch",
      "zipkin.storage.elasticsearch.ensure-templates=false",
      "zipkin.storage.elasticsearch.timeout=200",
      "zipkin.storage.elasticsearch.health-check.enabled=true",
      // uncomment (and also change log4j2.properties) to see health-checks requests in the console
      //"zipkin.storage.elasticsearch.health-check.http-logging=headers",
      "zipkin.storage.elasticsearch.health-check.interval=100ms",
      "zipkin.storage.elasticsearch.hosts=" + hosts)
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();
  }

  @Test void allHealthy() {
    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {

      // There's an initialization delay, so await instead of expect everything up now.
      awaitTimeout.untilAsserted(() -> assertThat(storage.check().ok()).isTrue());
    }
  }

  @Test void oneHealthy() {
    server1Health.setHealthy(false);

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      assertOk(storage.check());
    }
  }

  @Test void wrongScheme() {
    context.close();
    context = new AnnotationConfigApplicationContext();
    initWithHosts("https://localhost:" + server1.httpPort());

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isFalse();
      // Test this is not wrapped in a rejection exception, as health check is not throttled
      // Depending on JDK this is SSLHandshakeException or NotSslRecordException
      assertThat(result.error()).isInstanceOf(SSLException.class);
    }
  }

  @Test void noneHealthy() {
    server1Health.setHealthy(false);
    server2Health.setHealthy(false);

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isFalse();
      assertThat(result.error())
        .isInstanceOf(EndpointSelectionTimeoutException.class);
    }
  }

  // If this flakes, uncomment in initWithHosts and log4j2.properties
  @Test void healthyThenNotHealthyThenHealthy() {
    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      assertOk(storage.check());

      logger.info("setting server 1 and 2 unhealthy");
      server1Health.setHealthy(false);
      server2Health.setHealthy(false);

      awaitTimeout.untilAsserted(() -> assertThat(storage.check().ok()).isFalse());

      logger.info("setting server 1 healthy");
      server1Health.setHealthy(true);

      awaitTimeout.untilAsserted(() -> assertThat(storage.check().ok()).isTrue());
    }
  }

  @Test void notHealthyThenHealthyThenNotHealthy() {
    server1Health.setHealthy(false);
    server2Health.setHealthy(false);

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isFalse();

      server2Health.setHealthy(true);

      awaitTimeout.untilAsserted(() -> assertThat(storage.check().ok()).isTrue());

      server2Health.setHealthy(false);

      awaitTimeout.untilAsserted(() -> assertThat(storage.check().ok()).isFalse());
    }
  }

  @Test void healthCheckDisabled() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.ensure-templates=false",
      "zipkin.storage.elasticsearch.timeout=200",
      "zipkin.storage.elasticsearch.health-check.enabled=false",
      "zipkin.storage.elasticsearch.health-check.interval=100ms",
      "zipkin.storage.elasticsearch.hosts=127.0.0.1:" +
        server1.httpPort() + ",127.0.0.1:" + server2.httpPort())
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    server1Health.setHealthy(false);
    server2Health.setHealthy(false);

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      // Even though cluster health is false, we ignore that and continue to check index health,
      // which is correctly returned by our mock server.
      assertOk(storage.check());
    }
  }

  static void assertOk(CheckResult result) {
    if (!result.ok()) {
      Throwable error = result.error();
      throw new AssertionError("Health check failed with message: " + error.getMessage(), error);
    }
  }
}
