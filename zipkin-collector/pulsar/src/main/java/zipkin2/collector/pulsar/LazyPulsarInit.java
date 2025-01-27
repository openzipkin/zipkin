/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pulsar;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class LazyPulsarInit {

  private final Collector collector;
  private final CollectorMetrics metrics;
  private final String topic;
  private final int concurrency;
  private final Map<String, Object> clientProps, consumerProps;
  public volatile PulsarClient result;
  final AtomicReference<CheckResult> failure = new AtomicReference<>();

  LazyPulsarInit(PulsarCollector.Builder builder) {
    this.collector = builder.delegate.build();
    this.metrics = builder.metrics;
    this.topic = builder.topic;
    this.concurrency = builder.concurrency;
    this.clientProps = builder.clientProps;
    this.consumerProps = builder.consumerProps;
  }

  void init() {
    if (result == null) {
      synchronized (this) {
        if (result == null) {
          result = subscribe();
        }
      }
    }
  }

  private PulsarClient subscribe() {
    PulsarClient client;
    try {
      client = PulsarClient.builder()
          .loadConf(clientProps)
          .operationTimeout(6, TimeUnit.SECONDS)
          .connectionTimeout(12, TimeUnit.SECONDS)
          .build();
    } catch (Exception e) {
      failure.set(CheckResult.failed(e));
      throw new RuntimeException("Pulsar client creation failed" + e.getMessage(), e);
    }

    try {
      for (int i = 0; i < concurrency; i++) {
        PulsarSpanConsumer consumer = new PulsarSpanConsumer(topic, consumerProps, client, collector, metrics);
        consumer.startConsumer();
      }
      return client;
    } catch (Exception e) {
      try {
        client.close();
      } catch (PulsarClientException ex) {
        // Nobody cares me.
      }
      failure.set(CheckResult.failed(e));
      throw new RuntimeException("Pulsar Client is unable to subscribe to the topic(" + topic + "), please check the service.", e);
    }
  }

  void close() throws PulsarClientException {
    PulsarClient maybe = result;
    if (maybe != null) {
      result.close();
      result = null;
    }
  }
}