/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.ProducesJson;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.prometheus.client.CollectorRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthStatusHttpMapper;

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
      switch (meter.getId().getType()) {
        case COUNTER:
          metricsJson.put("counter." + name + "." + transport,
            ((Counter) meter).count());
          continue;
        case GAUGE:
          metricsJson.put("gauge." + name + "." + transport,
            ((Gauge) meter).value());
      }
    }
    return metricsJson;
  }

  // Delegates the health endpoint from the Actuator to the root context path and can be deprecated
  // in future in favour of Actuator endpoints
  @Get("/health")
  public HttpResponse getHealth() throws JsonProcessingException {
    Health health = healthEndpoint.health();

    Map<String, Object> healthJson = new LinkedHashMap<>();
    healthJson.put("status", health.getStatus().getCode());
    healthJson.put("zipkin", health.getDetails().get("zipkin"));
    byte[] body = mapper.writer().writeValueAsBytes(healthJson);

    ResponseHeaders headers = ResponseHeaders.builder(statusMapper.mapStatus(health.getStatus()))
      .contentType(MediaType.JSON)
      .setInt(HttpHeaderNames.CONTENT_LENGTH, body.length).build();
    return HttpResponse.of(headers, HttpData.of(body));
  }
}
