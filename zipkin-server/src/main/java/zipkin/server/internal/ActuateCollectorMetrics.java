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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import zipkin.collector.CollectorMetrics;
import zipkin.internal.Nullable;

import static zipkin.internal.Util.checkNotNull;

/**
 * This is a simple metric service that exports the following to the "/metrics" endpoint:
 *
 * <pre>
 * <ul>
 *     <li>counter.zipkin_collector.messages.$transport - cumulative messages received; should
 * relate to messages reported by instrumented apps</li>
 *     <li>counter.zipkin_collector.messages_dropped.$transport - cumulative messages dropped;
 * reasons include client disconnects or malformed content</li>
 *     <li>counter.zipkin_collector.bytes.$transport - cumulative message bytes</li>
 *     <li>counter.zipkin_collector.spans.$transport - cumulative spans read; should relate to
 * messages reported by instrumented apps</li>
 *     <li>counter.zipkin_collector.spans_dropped.$transport - cumulative spans dropped; reasons
 * include sampling or storage failures</li>
 *     <li>gauge.zipkin_collector.message_spans.$transport - last count of spans in a message</li>
 *     <li>gauge.zipkin_collector.message_bytes.$transport - last count of bytes in a message</li>
 * </ul>
 * </pre>
 *
 * See https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html
 *
 * <p>In-memory implementation mimics code from org.springframework.boot.actuate.metrics.buffer
 */

public final class ActuateCollectorMetrics implements CollectorMetrics {

  private MeterRegistry registryInstance;
  private final Counter messages;
  private final Counter messagesDropped;
  private final Counter bytes;
  private final Counter spans;
  private final Counter spansDropped;
  private AtomicInteger messageBytes;
  private AtomicInteger messageSpans;


  public ActuateCollectorMetrics(MeterRegistry registry) {
    this(null, registry);
  }

  ActuateCollectorMetrics(@Nullable String transport, MeterRegistry meterRegistry) {
    this.registryInstance = meterRegistry;
    String transportType = transport == null ? "" : "." + transport;
    this.messages = meterRegistry
      .counter("counter.zipkin_collector.messages" + transportType);
    this.messagesDropped = meterRegistry
      .counter("counter.zipkin_collector.messages_dropped" + transportType);
    this.messageBytes = meterRegistry
      .gauge("gauge.zipkin_collector.message_bytes" + transportType, new AtomicInteger(0));
    this.messageSpans = meterRegistry
      .gauge("gauge.zipkin_collector.message_spans" + transportType, new AtomicInteger(0));
    this.bytes = meterRegistry
      .counter("counter.zipkin_collector.bytes" + transportType);
    this.spans = meterRegistry
      .counter("counter.zipkin_collector.spans" + transportType);
    this.spansDropped = meterRegistry
      .counter("counter.zipkin_collector.spans_dropped" + transportType);
  }

  @Override public ActuateCollectorMetrics forTransport(String transportType) {
    checkNotNull(transportType, "transportType");
    return new ActuateCollectorMetrics(transportType, registryInstance);
  }

  @Override public void incrementMessages() {
    messages.increment();
  }

  @Override public void incrementMessagesDropped() {
    messagesDropped.increment();
  }

  @Override public void incrementSpans(int quantity) {
    messageSpans.set(quantity);
    spans.increment(quantity);
  }

  @Override public void incrementBytes(int quantity) {
    messageBytes.set(quantity);
    bytes.increment(quantity);
  }

  @Override public void incrementSpansDropped(int quantity) {
    spansDropped.increment(quantity);
  }
}
