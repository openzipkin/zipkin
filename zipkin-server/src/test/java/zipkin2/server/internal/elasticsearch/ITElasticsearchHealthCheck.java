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
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.junit4.server.ServerRule;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import zipkin2.CheckResult;
import zipkin2.elasticsearch.ElasticsearchStorage;

import static com.linecorp.armeria.common.HttpStatus.OK;
import static com.linecorp.armeria.common.MediaType.JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class ITElasticsearchHealthCheck {

  static final AggregatedHttpResponse YELLOW_RESPONSE = AggregatedHttpResponse.of(OK, JSON,
    "{\n"
      + "  \"cluster_name\": \"CollectorDBCluster\",\n"
      + "  \"status\": \"yellow\",\n"
      + "  \"timed_out\": false,\n"
      + "  \"number_of_nodes\": 1,\n"
      + "  \"number_of_data_nodes\": 1,\n"
      + "  \"active_primary_shards\": 5,\n"
      + "  \"active_shards\": 5,\n"
      + "  \"relocating_shards\": 0,\n"
      + "  \"initializing_shards\": 0,\n"
      + "  \"unassigned_shards\": 5,\n"
      + "  \"delayed_unassigned_shards\": 0,\n"
      + "  \"number_of_pending_tasks\": 0,\n"
      + "  \"number_of_in_flight_fetch\": 0,\n"
      + "  \"task_max_waiting_in_queue_millis\": 0,\n"
      + "  \"active_shards_percent_as_number\": 50\n"
      + "}\n");

  static final BlockingQueue<AggregatedHttpRequest> CAPTURED_REQUESTS = new LinkedBlockingQueue<>();

  @ClassRule public static ServerRule server = new ServerRule() {
    @Override protected void configure(ServerBuilder sb) throws Exception {
      sb.http(0);

      sb.serviceUnder("/", (ctx, req) -> {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        req.aggregate().thenAccept(agg -> {
          CAPTURED_REQUESTS.add(agg);
          responseFuture.complete(HttpResponse.of(YELLOW_RESPONSE));
        }).exceptionally(t -> {
          responseFuture.completeExceptionally(t);
          return null;
        });
        return HttpResponse.from(responseFuture);
      });
    }
  };

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After public void close() {
    CAPTURED_REQUESTS.clear();
  }

  /**
   * This blocks for less than the ready timeout of 1 second to prove we defer i/o until first use
   * of the storage component.
   */
  @Test(timeout = 950L) public void defersIOUntilFirstUse() throws IOException {
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234,127.0.0.1:5678")
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinElasticsearchStorageConfiguration.class);
    context.refresh();

    context.getBean(ElasticsearchStorage.class).close();
  }

  /** blocking a little is ok, but blocking forever is not. */
  @Test(timeout = 3000L) public void doesntHangWhenAllDown() throws IOException {
    TestPropertyValues.of(
      "spring.config.name=zipkin-server",
      "zipkin.storage.type:elasticsearch",
      "zipkin.storage.elasticsearch.hosts:127.0.0.1:1234,127.0.0.1:5678")
      .applyTo(context);
    context.register(
      PropertyPlaceholderAutoConfiguration.class,
      ZipkinElasticsearchStorageConfiguration.class);
    context.refresh();

    try (ElasticsearchStorage storage = context.getBean(ElasticsearchStorage.class)) {
      CheckResult result = storage.check();
      assertThat(result.ok()).isFalse();
      assertThat(result.error()).hasMessage(
        "couldn't connect any of [Endpoint{127.0.0.1:1234, weight=1000}, Endpoint{127.0.0.1:5678, weight=1000}]");
    }
  }
}
