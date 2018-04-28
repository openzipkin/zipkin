/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin.server.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsHealthController {
  private MeterRegistry meterRegistry;
  final JsonNodeFactory factory = JsonNodeFactory.instance;

  MetricsHealthController(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @GetMapping("/metrics")
  public ObjectNode fetchMetricsFromMicrometer() {
    ObjectNode metrics = factory.objectNode();
    // Iterate over the meters and get the Zipkin Custom meters for constructing the Metrics endpoint
    for (Meter meter : meterRegistry.getMeters()) {
      String name = meter.getId().getName();
      if (!name.startsWith("zipkin_collector")) continue;
      String transport = meter.getId().getTag("transport");
      if (transport == null) continue;
      switch (meter.getId().getType()) {
        case COUNTER:
          metrics.put("counter." + name + "." + transport,
            meterRegistry.get(name).counter().count());
          continue;
        case GAUGE:
          metrics.put("gauge." + name + "." + transport,
            meterRegistry.get(name).gauge().value());
      }
    }
    return metrics;
  }
}
