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
package zipkin2.server.internal.prometheus;

import com.fasterxml.jackson.core.JsonGenerator;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Get;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.prometheus.client.CollectorRegistry;
import java.io.IOException;
import java.io.StringWriter;
import zipkin2.server.internal.JsonUtil;

public class ZipkinMetricsController {

  final MeterRegistry meterRegistry;
  final CollectorRegistry collectorRegistry;

  ZipkinMetricsController(MeterRegistry meterRegistry, CollectorRegistry collectorRegistry) {
    this.meterRegistry = meterRegistry;
    this.collectorRegistry = collectorRegistry;
  }

  // Extracts Zipkin metrics to provide backward compatibility
  @Get("/metrics")
  public HttpResponse fetchMetricsFromMicrometer() throws IOException {
    StringWriter writer = new StringWriter();
    JsonGenerator generator = JsonUtil.createGenerator(writer);
    generator.writeStartObject();
    // Get the Zipkin Custom meters for constructing the Metrics endpoint
    for (Meter meter : meterRegistry.getMeters()) {
      String name = meter.getId().getName();
      if (!name.startsWith("zipkin_collector")) continue;
      String transport = meter.getId().getTag("transport");
      if (transport == null) continue;

      Meter.Type type = meter.getId().getType();
      if (type == Meter.Type.COUNTER) {
        generator.writeNumberField("counter." + name + "." + transport, ((Counter) meter).count());
      } else if (type == Meter.Type.GAUGE) {
        generator.writeNumberField("gauge." + name + "." + transport, ((Gauge) meter).value());
      } // We only use counters and gauges
    }
    generator.writeEndObject();
    generator.flush(); // instead of using try/finally as extra indent causes lines to wrap
    return HttpResponse.of(HttpStatus.OK, MediaType.JSON, writer.toString());
  }
}
