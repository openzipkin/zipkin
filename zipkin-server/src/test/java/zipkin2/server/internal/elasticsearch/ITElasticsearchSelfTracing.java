/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.testing.junit5.server.mock.MockWebServerExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.server.internal.brave.ZipkinSelfTracingConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.elasticsearch.TestResponses.VERSION_RESPONSE;
import static zipkin2.server.internal.elasticsearch.TestResponses.YELLOW_RESPONSE;

class ITElasticsearchSelfTracing {

  @RegisterExtension static MockWebServerExtension server = new MockWebServerExtension();

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
  ElasticsearchStorage storage;

  @BeforeEach void init() {
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.self-tracing.enabled=true",
      "zipkin.self-tracing.message-timeout=1ms",
      "zipkin.self-tracing.traces-per-second=10",
      "zipkin.storage.type=elasticsearch",
      "zipkin.storage.elasticsearch.ensure-templates=false",
      "zipkin.storage.elasticsearch.hosts=" + server.httpUri()).applyTo(context);
    Access.registerElasticsearch(context);
    context.register(ZipkinSelfTracingConfiguration.class);
    context.refresh();
    storage = context.getBean(ElasticsearchStorage.class);
  }

  @AfterEach void close() {
    storage.close();
  }

  /**
   * We currently don't have a nice way to mute outbound propagation in Brave. This just makes sure
   * we are nicer.
   */
  @Test void healthcheck_usesB3Single() {
    server.enqueue(VERSION_RESPONSE.toHttpResponse());
    server.enqueue(YELLOW_RESPONSE.toHttpResponse());

    assertThat(storage.check().ok()).isTrue();

    assertThat(server.takeRequest().request().headers())
      .extracting(e -> e.getKey().toString())
      .contains("b3")
      .doesNotContain("x-b3-traceid");
  }
}
