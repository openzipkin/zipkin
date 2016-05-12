/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.server;

import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
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
 */
final class ActuateCollectorMetrics implements CollectorMetrics {

  private final CounterService counterService;
  private final GaugeService gaugeService;
  private final String messages;
  private final String messagesDropped;
  private final String messageBytes;
  private final String messageSpans;
  private final String bytes;
  private final String spans;
  private final String spansDropped;

  ActuateCollectorMetrics(CounterService counterService, GaugeService gaugeService) {
    this(counterService, gaugeService, null);
  }

  ActuateCollectorMetrics(CounterService counterService, GaugeService gaugeService,
      @Nullable String transport) {
    this.counterService = counterService;
    this.gaugeService = gaugeService;
    String footer = transport == null ? "" : "." + transport;
    this.messages = "zipkin_collector.messages" + footer;
    this.messagesDropped = "zipkin_collector.messages_dropped" + footer;
    this.messageBytes = "zipkin_collector.message_bytes" + footer;
    this.messageSpans = "zipkin_collector.message_spans" + footer;
    this.bytes = "zipkin_collector.bytes" + footer;
    this.spans = "zipkin_collector.spans" + footer;
    this.spansDropped = "zipkin_collector.spans_dropped" + footer;
  }

  @Override public CollectorMetrics forTransport(String transportType) {
    checkNotNull(transportType, "transportType");
    return new ActuateCollectorMetrics(counterService, gaugeService, transportType);
  }

  @Override public void incrementMessages() {
    counterService.increment(messages);
  }

  @Override public void incrementMessagesDropped() {
    counterService.increment(messagesDropped);
  }

  @Override public void incrementSpans(int quantity) {
    gaugeService.submit(messageSpans, quantity);
    for (int i = 0; i < quantity; i++)
      counterService.increment(spans);
  }

  @Override public void incrementBytes(int quantity) {
    gaugeService.submit(messageBytes, quantity);
    for (int i = 0; i < quantity; i++)
      counterService.increment(bytes);
  }

  @Override
  public void incrementSpansDropped(int quantity) {
    for (int i = 0; i < quantity; i++)
      counterService.increment(spansDropped);
  }
}
