package zipkin.server.internal;
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsHealthController {

  @Autowired MeterRegistry meterRegistry;

  @Autowired HealthEndpoint healthEndpointDelegate;

  final JsonNodeFactory factory = JsonNodeFactory.instance;

  static final String messageCounter = "counter.zipkin_collector.messages.http";

  @GetMapping("/metrics")
  public ObjectNode fetchMetricsFromMicrometer(){
    ObjectNode node  = factory.objectNode();
    node.put(messageCounter, meterRegistry.get(messageCounter).counter().count());
    return node;
  }

  @ReadOperation
  @GetMapping("/health")
  public ResponseEntity<Health> getHealth(){
    return ResponseEntity.ok(healthEndpointDelegate.health());
  }
}
