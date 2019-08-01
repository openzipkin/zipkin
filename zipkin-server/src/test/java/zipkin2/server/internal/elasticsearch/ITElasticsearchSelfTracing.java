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

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.server.internal.brave.TracingConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.elasticsearch.TestResponses.YELLOW_RESPONSE;

public class ITElasticsearchSelfTracing {

  static final AtomicReference<AggregatedHttpRequest> CAPTURED_REQUEST = new AtomicReference<>();

  @ClassRule public static ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) {
      sb.serviceUnder("/", (ctx, req) -> HttpResponse.from(
        req.aggregate().thenApply(agg -> {
          CAPTURED_REQUEST.set(agg);
          return HttpResponse.of(YELLOW_RESPONSE);
        })));
    }
  };

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
  ElasticsearchStorage storage;

  @Before public void init() {
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.self-tracing.enabled=true",
      "zipkin.self-tracing.message-timeout=1ms",
      "zipkin.self-tracing.traces-per-second=10",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:" + server.httpUri("/")).applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      TracingConfiguration.class,
      ZipkinElasticsearchStorageConfiguration.class);
    context.refresh();
    storage = context.getBean(ElasticsearchStorage.class);
  }

  @After public void close() {
    CAPTURED_REQUEST.set(null);
  }

  /**
   * We currently don't have a nice way to mute outbound propagation in Brave. This just makes sure
   * we are nicer.
   */
  @Test public void healthcheck_usesB3Single() {
    assertThat(storage.check().ok()).isTrue();

    assertThat(CAPTURED_REQUEST.get().headers())
      .extracting(e -> e.getKey().toString())
      .contains("b3")
      .doesNotContain("x-b3-traceid");
  }
}
