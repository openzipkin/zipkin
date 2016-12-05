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
package zipkin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import zipkin.collector.CollectorMetrics;

import static zipkin.internal.Util.checkNotNull;

/**
 * @deprecated this class was accidentally put in the package "zipkin" instead of
 * "zipkin.collector". Will be removed in Zipkin 2.
 */
@Deprecated
public final class InMemoryCollectorMetrics implements CollectorMetrics {

  private final ConcurrentHashMap<String, AtomicInteger> metrics;
  private final String messages;
  private final String messagesDropped;
  private final String bytes;
  private final String spans;
  private final String spansDropped;

  public InMemoryCollectorMetrics() {
    this(new ConcurrentHashMap<>(), null);
  }

  InMemoryCollectorMetrics(ConcurrentHashMap<String, AtomicInteger> metrics, String transport) {
    this.metrics = metrics;
    this.messages = scope("messages", transport);
    this.messagesDropped = scope("messagesDropped", transport);
    this.bytes = scope("bytes", transport);
    this.spans = scope("spans", transport);
    this.spansDropped = scope("spansDropped", transport);
  }

  @Override public InMemoryCollectorMetrics forTransport(String transportType) {
    return new InMemoryCollectorMetrics(metrics, checkNotNull(transportType, "transportType"));
  }

  @Override public void incrementMessages() {
    increment(messages, 1);
  }

  public int messages() {
    return get(messages);
  }

  @Override public void incrementMessagesDropped() {
    increment(messagesDropped, 1);
  }

  public int messagesDropped() {
    return get(messagesDropped);
  }

  @Override public void incrementBytes(int quantity) {
    increment(bytes, quantity);
  }

  public int bytes() {
    return get(bytes);
  }

  @Override public void incrementSpans(int quantity) {
    increment(spans, quantity);
  }

  public int spans() {
    return get(spans);
  }

  @Override
  public void incrementSpansDropped(int quantity) {
    increment(spansDropped, quantity);
  }

  public int spansDropped() {
    return get(spansDropped);
  }

  public void clear() {
    metrics.clear();
  }

  private int get(String key) {
    AtomicInteger atomic = metrics.get(key);
    return atomic == null ? 0 : atomic.get();
  }

  private void increment(String key, int quantity) {
    if (quantity == 0) return;
    while (true) {
      AtomicInteger metric = metrics.get(key);
      if (metric == null) {
        metric = metrics.putIfAbsent(key, new AtomicInteger(quantity));
        if (metric == null) return; // won race creating the entry 
      }

      while (true) {
        int oldValue = metric.get();
        int update = oldValue + quantity;
        if (metric.compareAndSet(oldValue, update)) return; // won race updating
      }
    }
  }

  static String scope(String key, String transport) {
    return key + (transport == null ? "" : "." + transport);
  }
}
