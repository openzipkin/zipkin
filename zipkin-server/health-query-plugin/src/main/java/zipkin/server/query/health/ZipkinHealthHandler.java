/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
package zipkin.server.query.health;

import com.fasterxml.jackson.core.JsonGenerator;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Get;
import io.vavr.collection.Stream;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricFamily;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.stream.Collectors;

public class ZipkinHealthHandler {
  static final String STATUS_UP = "UP", STATUS_DOWN = "DOWN";

  final MetricsCollector collector;
  final private MetricsCreator metricsCreator;

  public static final MediaType MEDIA_TYPE_ACTUATOR =
      MediaType.parse("application/vnd.spring-boot.actuator.v2+json;charset=UTF-8");

  public ZipkinHealthHandler(ModuleManager moduleManager) {
    ModuleServiceHolder telemetry = moduleManager.find(TelemetryModule.NAME).provider();
    metricsCreator = telemetry.getService(MetricsCreator.class);
    collector = telemetry.getService(MetricsCollector.class);
  }

  @Get("/actuator/health")
  public HttpResponse getActuatorHealth(ServiceRequestContext ctx) {
    return newHealthResponse(MEDIA_TYPE_ACTUATOR);
  }

  @Get("/health")
  public HttpResponse getHealth(ServiceRequestContext ctx) {
    return newHealthResponse(MediaType.JSON_UTF_8);
  }

  HttpResponse newHealthResponse(MediaType mediaType) {
    final Map<MetricFamily.Sample, String> componentsHealth = Stream.ofAll(collector.collect())
        .flatMap(metricFamily -> metricFamily.samples)
        .filter(sample -> metricsCreator.isHealthCheckerMetrics(sample.name))
        .collect(Collectors.toMap(t -> t, t -> t.value > 0 ? STATUS_DOWN : STATUS_UP, (a, b) -> b));

    String overallStatus = STATUS_UP;
    for (String health : componentsHealth.values()) {
      if (STATUS_DOWN.equals(health)) {
        overallStatus = STATUS_DOWN;
        break;
      }
    }

    final String healthJson;
    try {
      healthJson = writeJson(overallStatus, componentsHealth);
    } catch (IOException e) {
      // Can't have an exception writing to a string.
      throw new Error(e);
    }
    return newHealthResponse(overallStatus, mediaType, healthJson);
  }

  static HttpResponse newHealthResponse(String status, MediaType mediaType, String healthJson) {
    HttpStatus code = status.equals(STATUS_UP) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return HttpResponse.of(code, mediaType, healthJson);
  }

  static String writeJson(String overallStatus, Map<MetricFamily.Sample, String> healths) throws IOException {
    StringWriter writer = new StringWriter();
    try (JsonGenerator generator = JsonUtil.createGenerator(writer)) {
      generator.writeStartObject();
      generator.writeStringField("status", overallStatus);
      generator.writeObjectFieldStart("zipkin");
      generator.writeStringField("status", overallStatus);
      generator.writeObjectFieldStart("details");

      for (Map.Entry<MetricFamily.Sample, String> health : healths.entrySet()) {
        generator.writeObjectFieldStart(health.getKey().name);
        generator.writeStringField("status", health.getValue());
        generator.writeEndObject(); // .zipkin.details.healthName
      }

      generator.writeEndObject(); // .zipkin.details
      generator.writeEndObject(); // .zipkin
      generator.writeEndObject(); // .
    }
    return writer.toString();
  }
}
