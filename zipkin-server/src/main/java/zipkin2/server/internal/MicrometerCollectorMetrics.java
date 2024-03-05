/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import zipkin2.collector.CollectorMetrics;
import zipkin2.internal.Nullable;

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
public final class MicrometerCollectorMetrics implements CollectorMetrics {
  final MeterRegistry registryInstance;
  final Counter messages, messagesDropped, bytes, spans, spansDropped;
  final AtomicInteger messageBytes, messageSpans;

  public MicrometerCollectorMetrics(MeterRegistry registry) {
    this(null, registry);
  }

  MicrometerCollectorMetrics(@Nullable String transport, MeterRegistry meterRegistry) {
    this.registryInstance = meterRegistry;
    if (transport == null) {
      messages = messagesDropped = bytes = spans = spansDropped = null;
      messageBytes = messageSpans = null;
      return;
    }
    this.messages =
      Counter.builder("zipkin_collector.messages")
        .description("cumulative amount of messages received")
        .tag("transport", transport)
        .register(registryInstance);
    this.messagesDropped =
      Counter.builder("zipkin_collector.messages_dropped")
        .description("cumulative amount of messages received that were later dropped")
        .tag("transport", transport)
        .register(registryInstance);

    this.bytes =
        Counter.builder("zipkin_collector.bytes")
            .description("cumulative amount of bytes received")
            .tag("transport", transport)
            .baseUnit("bytes")
            .register(registryInstance);
    this.spans =
        Counter.builder("zipkin_collector.spans")
            .description("cumulative amount of spans received")
            .tag("transport", transport)
            .register(registryInstance);
    this.spansDropped =
        Counter.builder("zipkin_collector.spans_dropped")
            .description("cumulative amount of spans received that were later dropped")
            .tag("transport", transport)
            .register(registryInstance);

    this.messageSpans = new AtomicInteger(0);
    Gauge.builder("zipkin_collector.message_spans", messageSpans, AtomicInteger::get)
        .description("count of spans per message")
        .tag("transport", transport)
        .register(registryInstance);
    this.messageBytes = new AtomicInteger(0);
    Gauge.builder("zipkin_collector.message_bytes", messageBytes, AtomicInteger::get)
        .description("size of a message containing serialized spans")
        .tag("transport", transport)
        .baseUnit("bytes")
        .register(registryInstance);
  }

  @Override
  public MicrometerCollectorMetrics forTransport(String transportType) {
    if (transportType == null) throw new NullPointerException("transportType == null");
    return new MicrometerCollectorMetrics(transportType, registryInstance);
  }

  @Override
  public void incrementMessages() {
    checkScoped();
    messages.increment();
  }

  @Override
  public void incrementMessagesDropped() {
    checkScoped();
    messagesDropped.increment();
  }

  @Override
  public void incrementSpans(int quantity) {
    checkScoped();
    messageSpans.set(quantity);
    spans.increment(quantity);
  }

  @Override
  public void incrementBytes(int quantity) {
    checkScoped();
    messageBytes.set(quantity);
    bytes.increment(quantity);
  }

  @Override
  public void incrementSpansDropped(int quantity) {
    checkScoped();
    spansDropped.increment(quantity);
  }

  void checkScoped() {
    if (messages == null) {
      throw new IllegalStateException("always scope with ActuateCollectorMetrics.forTransport");
    }
  }
}
