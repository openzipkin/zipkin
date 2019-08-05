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
package zipkin2.server.internal.elasticsearch;

import java.io.IOException;
import org.junit.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static org.assertj.core.api.Assertions.assertThat;

public class ITElasticsearchClientInitialization {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  /**
   * This blocks for less than the timeout of 2 second to prove we defer i/o until first use of the
   * storage component.
   */
  @Test(timeout = 1900L) public void defersIOUntilFirstUse() throws IOException {
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
  @Test(timeout = 3000L) public void doesntHangWhenAllDown() throws IOException {
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
