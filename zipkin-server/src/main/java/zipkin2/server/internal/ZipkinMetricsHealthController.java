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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import zipkin2.Component;

import static com.linecorp.armeria.common.HttpHeaderNames.CONTENT_LENGTH;
import static zipkin2.server.internal.ComponentHealth.STATUS_DOWN;
import static zipkin2.server.internal.ComponentHealth.STATUS_UP;
import static zipkin2.server.internal.ZipkinServerConfiguration.MEDIA_TYPE_ACTUATOR;

public class ZipkinMetricsHealthController {
  final List<Component> components;
  final MeterRegistry meterRegistry;
  final CollectorRegistry collectorRegistry;
  final ObjectMapper mapper;
  final JsonNodeFactory factory = JsonNodeFactory.instance;

  ZipkinMetricsHealthController(
    List<Component> components,
    MeterRegistry meterRegistry,
    CollectorRegistry collectorRegistry,
    ObjectMapper mapper
  ) {
    this.components = components;
    this.meterRegistry = meterRegistry;
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

  @Get("/actuator/health")
  public CompletableFuture<HttpResponse> getActuatorHealth(ServiceRequestContext ctx) {
    return health(ctx, MEDIA_TYPE_ACTUATOR);
  }

  @Get("/health")
  public CompletableFuture<HttpResponse> getHealth(ServiceRequestContext ctx) {
    return health(ctx, MediaType.JSON_UTF_8);
  }

  CompletableFuture<HttpResponse> health(ServiceRequestContext ctx, MediaType mediaType) {
    CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
    ctx.setRequestTimeoutHandler(() -> {
      Map<String, Object> healthJson = new LinkedHashMap<>();
      healthJson.put("status", STATUS_DOWN);
      healthJson.put("zipkin", "Timed out computing health status. "
        + "This often means your storage backend is unreachable.");
      try {
        responseFuture.complete(HttpResponse.of(constructHealthResponse(healthJson, mediaType)));
      } catch (Throwable e) {
        // Shouldn't happen since we serialize to an array.
        responseFuture.completeExceptionally(e);
      }
    });

    ctx.blockingTaskExecutor().execute(() -> {
      Map<String, Object> healthJson = aggregateStatus(components);
      try {
        responseFuture.complete(HttpResponse.of(constructHealthResponse(healthJson, mediaType)));
      } catch (Throwable e) {
        // Shouldn't happen since we serialize to an array.
        responseFuture.completeExceptionally(e);
      }
    });
    return responseFuture;
  }

  static Map<String, Object> aggregateStatus(List<Component> components) {
    String status = STATUS_UP;

    Map<String, ComponentHealth> zipkinDetails = components.stream()
      .parallel().map(ComponentHealth::ofComponent)
      .collect(Collectors.toMap(ComponentHealth::getName, c -> c));

    for (ComponentHealth componentHealth : zipkinDetails.values()) {
      if (componentHealth.getStatus().equals(STATUS_DOWN)) status = STATUS_DOWN;
    }

    Map<String, Object> zipkinHealth = new LinkedHashMap<>();
    zipkinHealth.put("status", status);
    zipkinHealth.put("details", zipkinDetails);

    Map<String, Object> healthJson = new LinkedHashMap<>();
    healthJson.put("status", status);
    healthJson.put("zipkin", zipkinHealth);
    return healthJson;
  }

  AggregatedHttpResponse constructHealthResponse(Map<String, Object> json, MediaType mediaType)
    throws IOException {
    byte[] body = mapper.writeValueAsBytes(json);
    int code = json.get("status").equals(STATUS_UP) ? 200 : 503;
    ResponseHeaders headers = ResponseHeaders.builder(code)
      .contentType(mediaType)
      .setInt(CONTENT_LENGTH, body.length).build();
    return AggregatedHttpResponse.of(headers, HttpData.wrap(body));
  }
}
