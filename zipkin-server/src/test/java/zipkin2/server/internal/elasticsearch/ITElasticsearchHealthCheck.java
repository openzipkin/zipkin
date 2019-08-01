package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.healthcheck.HealthCheckService;
import com.linecorp.armeria.server.healthcheck.SettableHealthChecker;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static zipkin2.server.internal.elasticsearch.TestResponses.GREEN_RESPONSE;

public class ITElasticsearchHealthCheck {

  static final SettableHealthChecker server1Health = new SettableHealthChecker(true);

  @ClassRule public static ServerRule server1 = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) {
      sb.service("/_cluster/health", HealthCheckService.of(server1Health));
      sb.serviceUnder("/_cluster/health/", (ctx, req) -> HttpResponse.of(GREEN_RESPONSE));
    }
  };

  static final SettableHealthChecker server2Health = new SettableHealthChecker(true);

  @ClassRule public static ServerRule server2 = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) {
      sb.service("/_cluster/health", HealthCheckService.of(server2Health));
      sb.serviceUnder("/_cluster/health/", (ctx, req) -> HttpResponse.of(GREEN_RESPONSE));
    }
  };

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @Before public void setUp() {
    server1Health.setHealthy(true);
    server2Health.setHealthy(true);

    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.timeout:200",
      "zipkin.storage.elasticsearch.health-check.enabled:true",
      "zipkin.storage.elasticsearch.health-check.interval:100ms",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:" +
        server1.httpPort() + ",127.0.0.1:" + server2.httpPort())
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinElasticsearchStorageConfiguration.class);
    context.refresh();
  }

  @Test public void allHealthy() throws Exception {
    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isTrue();
    }
  }

  @Test public void oneHealthy() throws Exception {
    server1Health.setHealthy(false);

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isTrue();
    }
  }

  @Test public void noneHealthy() throws Exception {
    server1Health.setHealthy(false);
    server2Health.setHealthy(false);

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isFalse();
    }
  }

  @Test public void healthyThenNotHealthyThenHealthy() throws Exception {
    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isTrue();

      server1Health.setHealthy(false);
      server2Health.setHealthy(false);

      // Health check interval is 100ms
      await().timeout(300, TimeUnit.MILLISECONDS).untilAsserted(() ->
        assertThat(storage.check().ok()).isFalse());

      server1Health.setHealthy(true);

      // Health check interval is 100ms
      await().timeout(300, TimeUnit.MILLISECONDS).untilAsserted(() ->
        assertThat(storage.check().ok()).isTrue());
    }
  }

  @Test public void notHealthyThenHealthyThenNotHealthy() throws Exception {
    server1Health.setHealthy(false);
    server2Health.setHealthy(false);

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isFalse();

      server2Health.setHealthy(true);

      // Health check interval is 100ms
      await().timeout(300, TimeUnit.MILLISECONDS).untilAsserted(() ->
        assertThat(storage.check().ok()).isTrue());

      server2Health.setHealthy(false);

      // Health check interval is 100ms
      await().timeout(300, TimeUnit.MILLISECONDS).untilAsserted(() ->
        assertThat(storage.check().ok()).isFalse());
    }
  }

  @Test public void healthCheckDisabled() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.timeout:200",
      "zipkin.storage.elasticsearch.health-check.enabled:false",
      "zipkin.storage.elasticsearch.health-check.interval:100ms",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:" +
        server1.httpPort() + ",127.0.0.1:" + server2.httpPort())
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinElasticsearchStorageConfiguration.class);
    context.refresh();

    server1Health.setHealthy(false);
    server2Health.setHealthy(false);

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      // Even though cluster health is false, we ignore that and continue to check index health,
      // which is correctly returned by our mock server.
      assertThat(result.ok()).isTrue();
    }
  }
}
