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

package zipkin.server.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Get;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.apache.logging.log4j.core.util.StringBuilderWriter;

import java.io.IOException;
import java.util.Enumeration;

public class ZipkinTelemetryHandler {
  private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private final CollectorRegistry registry = CollectorRegistry.defaultRegistry;

  @Get("/metrics")
  public AggregatedHttpResponse metrics() throws IOException {
    final JsonObject jsonObject = new JsonObject();
    final Enumeration<Collector.MetricFamilySamples> samples = registry.metricFamilySamples();
    while (samples.hasMoreElements()) {
      final Collector.MetricFamilySamples sampleFamily = samples.nextElement();
      final JsonArray sampleFamilyJsonArray = new JsonArray();
      jsonObject.add(sampleFamily.name, sampleFamilyJsonArray);
      for (Collector.MetricFamilySamples.Sample sample : sampleFamily.samples) {
        final JsonObject sampleJson = new JsonObject();
        final JsonObject meterTags = new JsonObject();
        sampleJson.add("tags", meterTags);
        for (int i = 0; i < sample.labelNames.size(); i++) {
          meterTags.addProperty(sample.labelNames.get(i), sample.labelValues.get(i));
        }
        sampleJson.addProperty("type", sampleFamily.type.name());
        sampleJson.addProperty("value", sample.value);
        sampleFamilyJsonArray.add(sampleJson);
      }
    }
    return AggregatedHttpResponse.of(HttpStatus.OK, MediaType.JSON, HttpData.ofUtf8(gson.toJson(jsonObject)));
  }

  @Get("/prometheus")
  public AggregatedHttpResponse prometheus() throws IOException {
    StringBuilderWriter buf = new StringBuilderWriter();
    TextFormat.write004(buf, registry.metricFamilySamples());
    return AggregatedHttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, HttpData.ofUtf8(buf.toString()));
  }
}
