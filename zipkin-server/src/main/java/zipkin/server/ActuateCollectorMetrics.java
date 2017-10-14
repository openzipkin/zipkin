/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.buffer.BufferMetricReader;
import org.springframework.boot.actuate.metrics.buffer.CounterBuffers;
import org.springframework.boot.actuate.metrics.buffer.GaugeBuffers;
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
public final class ActuateCollectorMetrics implements CollectorMetrics, PublicMetrics
{
  private final CounterBuffers counterBuffers;
  private final GaugeBuffers gaugeBuffers;
  private final String messages;
  private final String messagesDropped;
  private final String messageBytes;
  private final String messageSpans;
  private final String bytes;
  private final String spans;
  private final String spansDropped;
  private final BufferMetricReader reader;

  public ActuateCollectorMetrics(CounterBuffers counterBuffers, GaugeBuffers gaugeBuffers) {
    this(counterBuffers, gaugeBuffers, null);
  }

  ActuateCollectorMetrics(CounterBuffers counterBuffers, GaugeBuffers gaugeBuffers,
      @Nullable String transport) {
    this.counterBuffers = counterBuffers;
    this.gaugeBuffers = gaugeBuffers;
    this.reader = new BufferMetricReader(counterBuffers, gaugeBuffers);
    String footer = transport == null ? "" : "." + transport;
    this.messages = "counter.zipkin_collector.messages" + footer;
    this.messagesDropped = "counter.zipkin_collector.messages_dropped" + footer;
    this.messageBytes = "gauge.zipkin_collector.message_bytes" + footer;
    this.messageSpans = "gauge.zipkin_collector.message_spans" + footer;
    this.bytes = "counter.zipkin_collector.bytes" + footer;
    this.spans = "counter.zipkin_collector.spans" + footer;
    this.spansDropped = "counter.zipkin_collector.spans_dropped" + footer;
  }

  @Override public ActuateCollectorMetrics forTransport(String transportType) {
    checkNotNull(transportType, "transportType");
    return new ActuateCollectorMetrics(counterBuffers, gaugeBuffers, transportType);
  }

  @Override
  public Collection<Metric<?>> metrics()
  {
    final Iterable<Metric<?>> metrics = reader.findAll();

    final List<Metric<?>> result = new ArrayList<>();
    metrics.forEach(result::add);
    return result;
  }

  @Override public void incrementMessages() {
    counterBuffers.increment(messages, 1L);
  }

  @Override public void incrementMessagesDropped() {
    counterBuffers.increment(messagesDropped, 1L);
  }

  @Override public void incrementSpans(int quantity) {
    gaugeBuffers.set(messageSpans, quantity);
    counterBuffers.increment(spans, quantity);
  }

  @Override public void incrementBytes(int quantity) {
    gaugeBuffers.set(messageBytes, quantity);
    counterBuffers.increment(bytes, quantity);
  }

  @Override
  public void incrementSpansDropped(int quantity) {
    counterBuffers.increment(spansDropped, quantity);
  }

  // visible for testing
  void reset() {
    counterBuffers.reset(messages);
    counterBuffers.reset(messagesDropped);
    counterBuffers.reset(bytes);
    counterBuffers.reset(spans);
    counterBuffers.reset(spansDropped);
    gaugeBuffers.set(messageSpans, 0);
    gaugeBuffers.set(messageBytes, 0);
  }
}
