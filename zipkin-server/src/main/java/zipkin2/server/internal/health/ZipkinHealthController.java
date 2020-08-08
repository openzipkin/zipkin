/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.server.internal.health;

import com.fasterxml.jackson.core.JsonGenerator;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Get;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import zipkin2.Component;
import zipkin2.server.internal.JsonUtil;

import static zipkin2.server.internal.ZipkinHttpConfiguration.MEDIA_TYPE_ACTUATOR;
import static zipkin2.server.internal.health.ComponentHealth.STATUS_DOWN;
import static zipkin2.server.internal.health.ComponentHealth.STATUS_UP;

public class ZipkinHealthController {
  final List<Component> components;

  ZipkinHealthController(List<Component> components) {
    this.components = components;
  }

  @Get("/actuator/health")
  public CompletableFuture<HttpResponse> getActuatorHealth(ServiceRequestContext ctx) {
    return health(ctx, MEDIA_TYPE_ACTUATOR);
  }

  @Get("/health")
  public CompletableFuture<HttpResponse> getHealth(ServiceRequestContext ctx) {
    return health(ctx, MediaType.JSON_UTF_8);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  CompletableFuture<HttpResponse> health(ServiceRequestContext ctx, MediaType mediaType) {
    CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
    ctx.whenRequestTimingOut().handle((unused, unused2) -> {
      try {
        String healthJson = writeJsonError("Timed out computing health status. "
          + "This often means your storage backend is unreachable.");
        responseFuture.complete(newHealthResponse(STATUS_DOWN, mediaType, healthJson));
      } catch (Throwable e) {
        // Shouldn't happen since we serialize to an array.
        responseFuture.completeExceptionally(e);
      }
      return null;
    });

    List<CompletableFuture<ComponentHealth>> futures =  components.stream()
      .map(component ->
        CompletableFuture.supplyAsync(
          () -> ComponentHealth.ofComponent(component),
          // Computing health of a component may block so we make sure to invoke in the blocking
          // executor.
          ctx.blockingTaskExecutor()))
      .collect(Collectors.toList());

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .handle((unused, t) -> {
        if (t != null) {
          responseFuture.completeExceptionally(t);
        } else {
          responseFuture.complete(newHealthResponse(
            futures.stream()
              .map(CompletableFuture::join)
              .collect(Collectors.toList()),
            mediaType));
        }
        return null;
      });

    return responseFuture;
  }

  static HttpResponse newHealthResponse(List<ComponentHealth> healths, MediaType mediaType) {

    String overallStatus = STATUS_UP;
    for (ComponentHealth health : healths) {
      if (health.status.equals(STATUS_DOWN)) overallStatus = STATUS_DOWN;
    }

    final String healthJson;
    try {
      healthJson = writeJson(overallStatus, healths);
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

  static String writeJsonError(String error) throws IOException {
    StringWriter writer = new StringWriter();
    try (JsonGenerator generator = JsonUtil.createGenerator(writer)) {
      generator.writeStartObject();
      generator.writeStringField("status", STATUS_DOWN);
      generator.writeObjectFieldStart("zipkin");
      generator.writeStringField("status", STATUS_DOWN);
      generator.writeObjectFieldStart("details");
      generator.writeStringField("error", error);
      generator.writeEndObject(); // .zipkin.details
      generator.writeEndObject(); // .zipkin
      generator.writeEndObject(); // .
    }
    return writer.toString();
  }

  static String writeJson(String overallStatus, List<ComponentHealth> healths) throws IOException {
    StringWriter writer = new StringWriter();
    try (JsonGenerator generator = JsonUtil.createGenerator(writer)) {
      generator.writeStartObject();
      generator.writeStringField("status", overallStatus);
      generator.writeObjectFieldStart("zipkin");
      generator.writeStringField("status", overallStatus);
      generator.writeObjectFieldStart("details");

      for (ComponentHealth health : healths) {
        generator.writeObjectFieldStart(health.name);
        generator.writeStringField("status", health.status);

        if (health.status.equals(STATUS_DOWN)) {
          generator.writeObjectFieldStart("details");
          generator.writeStringField("error", health.error);
          generator.writeEndObject(); // .zipkin.details.healthName.details
        }

        generator.writeEndObject(); // .zipkin.details.healthName
      }

      generator.writeEndObject(); // .zipkin.details
      generator.writeEndObject(); // .zipkin
      generator.writeEndObject(); // .
    }
    return writer.toString();
  }
}
