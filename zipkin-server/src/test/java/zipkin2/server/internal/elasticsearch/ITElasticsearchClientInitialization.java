/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static org.assertj.core.api.Assertions.assertThat;

class ITElasticsearchClientInitialization {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  /**
   * This blocks for less than the timeout of 2 second to prove we defer i/o until first use of the
   * storage component.
   */
  @Test @Timeout(1900L) void defersIOUntilFirstUse() {
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.timeout:2000",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234,127.0.0.1:5678")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    context.getBean(ElasticsearchStorage.class).close();
  }

  /** blocking a little is ok, but blocking forever is not. */
  @Test @Timeout(3000L) void doesntHangWhenAllDown() {
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.timeout:1000",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234,127.0.0.1:5678")
      .applyTo(context);
    Access.registerElasticsearch(context);
    context.refresh();

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isFalse();
    }
  }
}
