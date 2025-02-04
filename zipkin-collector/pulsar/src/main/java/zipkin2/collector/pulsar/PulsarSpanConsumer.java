/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pulsar;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Callback;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;

import java.io.Closeable;
import java.util.Map;

public class PulsarSpanConsumer implements Closeable {
  static final Callback<Void> NOOP = new Callback<>() {
    @Override public void onSuccess(Void value) {
    }

    @Override public void onError(Throwable t) {
    }
  };

  private static final Logger LOG = LoggerFactory.getLogger(PulsarSpanConsumer.class);
  private final String topic;
  private final Map<String, Object> consumerProps;
  private final PulsarClient client;
  private final Collector collector;
  private final CollectorMetrics metrics;
  private Consumer<byte[]> consumer;

  public PulsarSpanConsumer(String topic, Map<String, Object> consumerProps, PulsarClient client, Collector collector, CollectorMetrics metrics) {
    this.topic = topic;
    this.consumerProps = consumerProps;
    this.client = client;
    this.collector = collector;
    this.metrics = metrics;
  }

  public void startConsumer() throws PulsarClientException {
    consumer = client.newConsumer()
        .topic(topic)
        .subscriptionType(SubscriptionType.Shared)
        .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
        .loadConf(consumerProps)
        .messageListener(new ZipkinMessageListener<>(collector, metrics))
        .subscribe();
  }

  @Override public void close() {
    try {
      if (consumer != null) {
        consumer.close();
        consumer = null;
      }
    } catch (PulsarClientException e) {
      LOG.error("Failed to close Pulsar Consumer client.", e);
    }
  }

  /**
   * A message listener implementation for processing messages in a Pulsar consumer,
   * and it should not be overridden by loadConf as it ensures that zipkin could handle span correctly.
   */
  record ZipkinMessageListener<T>(Collector collector, CollectorMetrics metrics) implements MessageListener<T> {

    @Override public void received(Consumer<T> consumer, Message<T> msg) {
      try {
        final byte[] serialized = msg.getData();
        metrics.incrementMessages();
        metrics.incrementBytes(serialized.length);

        if (serialized.length == 0) return; // lenient on empty messages

        collector.acceptSpans(serialized, NOOP);
        consumer.acknowledgeAsync(msg);
      } catch (Throwable th) {
        metrics.incrementMessagesDropped();
        LOG.error("Pulsar Span Consumer failed to process the message.", th);
        consumer.negativeAcknowledge(msg);
      }
    }
  }
}
