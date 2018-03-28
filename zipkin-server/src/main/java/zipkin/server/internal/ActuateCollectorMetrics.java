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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.metrics.Metric;
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
public final class ActuateCollectorMetrics implements CollectorMetrics, PublicMetrics {
  private final ConcurrentHashMap<String, CounterBuffer> counterBuffers;
  private final ConcurrentHashMap<String, GaugeBuffer> gaugeBuffers;
  private final String messages;
  private final String messagesDropped;
  private final String messageBytes;
  private final String messageSpans;
  private final String bytes;
  private final String spans;
  private final String spansDropped;

  public ActuateCollectorMetrics() {
    this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), null);
  }

  ActuateCollectorMetrics(
    ConcurrentHashMap<String, CounterBuffer> counterBuffers,
    ConcurrentHashMap<String, GaugeBuffer> gaugeBuffers,
    @Nullable String transport
  ) {
    this.counterBuffers = counterBuffers;
    this.gaugeBuffers = gaugeBuffers;
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

  @Override public void incrementMessages() {
    increment(messages, 1L);
  }

  @Override public void incrementMessagesDropped() {
    increment(messagesDropped, 1L);
  }

  @Override public void incrementSpans(int quantity) {
    set(messageSpans, quantity);
    increment(spans, quantity);
  }

  @Override public void incrementBytes(int quantity) {
    set(messageBytes, quantity);
    increment(bytes, quantity);
  }

  @Override public void incrementSpansDropped(int quantity) {
    increment(spansDropped, quantity);
  }

  // visible for testing
  void clear() {
    counterBuffers.clear();
    gaugeBuffers.clear();
  }

  @Override public Collection<Metric<?>> metrics() {
    List<Metric<?>> metrics = new ArrayList<>();
    gaugeBuffers.forEach((key, gauge) -> metrics.add(gauge.toMetric(key)));
    counterBuffers.forEach((key, counter) -> metrics.add(counter.toMetric(key)));
    return metrics;
  }

  void increment(String name, long delta) {
    CounterBuffer buffer = computeIfAbsent(counterBuffers, name, CounterBuffer::new);
    buffer.timestamp = System.currentTimeMillis();
    buffer.count.add(delta);
  }

  static class CounterBuffer {
    volatile long timestamp;
    final LongAdder count = new LongAdder();

    Metric<Long> toMetric(String key) {
      return new Metric<>(key, count.sum(), new Date(timestamp));
    }
  }

  void set(String name, double value) {
    GaugeBuffer buffer = computeIfAbsent(gaugeBuffers, name, GaugeBuffer::new);
    buffer.timestamp = System.currentTimeMillis();
    buffer.value = value;
  }

  static class GaugeBuffer {
    volatile long timestamp;
    volatile double value;

    Metric<Double> toMetric(String key) {
      return new Metric<>(key, value, new Date(timestamp));
    }
  }

  // optimization from org.springframework.boot.actuate.metrics.buffer carried over
  static <K, V> V computeIfAbsent(ConcurrentHashMap<K, V> map, K key, Supplier<V> valueFunction) {
    V value = map.get(key);
    if (value != null) return value;
    return map.computeIfAbsent(key, ignored -> valueFunction.get());
  }
}
