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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.ProducesJson;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.prometheus.client.CollectorRegistry;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthStatusHttpMapper;
import org.springframework.boot.actuate.health.Status;

public class MetricsHealthController {

  final MeterRegistry meterRegistry;
  final HealthEndpoint healthEndpoint;
  final HealthStatusHttpMapper statusMapper;
  final CollectorRegistry collectorRegistry;
  final ObjectMapper mapper;
  final JsonNodeFactory factory = JsonNodeFactory.instance;

  MetricsHealthController(
    MeterRegistry meterRegistry,
    HealthEndpoint healthEndpoint,
    HealthStatusHttpMapper statusMapper,
    CollectorRegistry collectorRegistry,
    ObjectMapper mapper
  ) {
    this.meterRegistry = meterRegistry;
    this.healthEndpoint = healthEndpoint;
    this.statusMapper = statusMapper;
    this.collectorRegistry = collectorRegistry;
    this.mapper = mapper;
  }

  // Extracts Zipkin metrics to provide backward compatibility
  @Get("/metrics")
  @ProducesJson
  public ObjectNode fetchMetricsFromMicrometer() {
    ObjectNode metricsJson = factory.objectNode();
    // Get the Zipkin Custom meters for constructing the Metrics endpoint
    for (Meter meter : meterRegistry.getMeters()) {
      String name = meter.getId().getName();
      if (!name.startsWith("zipkin_collector")) continue;
      String transport = meter.getId().getTag("transport");
      if (transport == null) continue;

      Meter.Type type = meter.getId().getType();
      if (type == Meter.Type.COUNTER) {
        metricsJson.put("counter." + name + "." + transport, ((Counter) meter).count());
      } else if (type == Meter.Type.GAUGE) {
        metricsJson.put("gauge." + name + "." + transport, ((Gauge) meter).value());
      } // We only use counters and gauges
    }
    return metricsJson;
  }

  // Delegates the health endpoint from the Actuator to the root context path and can be deprecated
  // in future in favour of Actuator endpoints
  @Get("/health")
  public CompletableFuture<HttpResponse> getHealth(ServiceRequestContext ctx) {
    CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
    ctx.setRequestTimeoutHandler(() -> {
      Map<String, Object> healthJson = new LinkedHashMap<>();
      healthJson.put("status", Status.DOWN);
      healthJson.put("zipkin", "Timed out computing health status. "
        + "This often means your storage backend is unreachable.");
      try {
        responseFuture.complete(HttpResponse.of(
          constructHealthResponse(Status.DOWN, healthJson)));
      } catch (IOException e) {
        // Shouldn't happen since we serialize to an array.
        responseFuture.completeExceptionally(e);
      }
    });

    ctx.blockingTaskExecutor().execute(() -> {
      Health health = healthEndpoint.health();

      Map<String, Object> healthJson = new LinkedHashMap<>();
      healthJson.put("status", health.getStatus().getCode());
      healthJson.put("zipkin", health.getDetails().get("zipkin"));
      try {
        responseFuture.complete(HttpResponse.of(
          constructHealthResponse(health.getStatus(), healthJson)));
      } catch (IOException e) {
        // Shouldn't happen since we serialize to an array.
        responseFuture.completeExceptionally(e);
        return;
      }
    });
    return responseFuture;
  }

  private AggregatedHttpResponse constructHealthResponse(
    Status status, Map<String, Object> healthJson)
    throws IOException {
    byte[] body = mapper.writeValueAsBytes(healthJson);
    ResponseHeaders headers = ResponseHeaders.builder(statusMapper.mapStatus(status))
      .contentType(MediaType.JSON)
      .setInt(HttpHeaderNames.CONTENT_LENGTH, body.length).build();
    return AggregatedHttpResponse.of(headers, HttpData.wrap(body));
  }
}
