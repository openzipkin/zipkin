/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.pulsar;

import io.opentelemetry.api.internal.StringUtils;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** This collector consumes encoded binary messages from a Pulsar topic. */
public final class PulsarCollector extends CollectorComponent {

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a Pulsar topic. */
  public static final class Builder extends CollectorComponent.Builder {
    final Collector.Builder delegate = Collector.newBuilder(PulsarCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    Map<String, Object> clientProps = new HashMap<>();
    Map<String, Object> consumerProps = new HashMap<>();
    String topic = "zipkin";
    int concurrency = 1;

    @Override
    public Builder storage(StorageComponent storage) {
      delegate.storage(storage);
      return this;
    }

    @Override
    public Builder metrics(CollectorMetrics metrics) {
      if (Objects.isNull(metrics)) throw new NullPointerException("metrics == null");
      this.metrics = metrics.forTransport("pulsar");
      this.delegate.metrics(this.metrics);
      return this;
    }

    @Override
    public Builder sampler(CollectorSampler sampler) {
      this.delegate.sampler(sampler);
      return this;
    }

    @Override
    public PulsarCollector build() {
      return new PulsarCollector(this);
    }

    /** Count of concurrent message consumers on the topic. Defaults to 1. */
    public Builder concurrency(Integer concurrency) {
      if (concurrency < 1) throw new IllegalArgumentException("concurrency < 1");
      this.concurrency = concurrency;
      return this;
    }

    /** Queue zipkin spans will be consumed from. Defaults to "zipkin". */
    public Builder topic(String topic) {
      if (StringUtils.isNullOrEmpty(topic)) throw new NullPointerException("topic is null or empty");
      this.topic = topic;
      return this;
    }

    /** The service URL for the Pulsar client ex. pulsar://my-broker:6650. No default. */
    public Builder serviceUrl(String serviceUrl) {
      if (StringUtils.isNullOrEmpty(serviceUrl)) throw new NullPointerException("serviceUrl is null or empty");
      clientProps.put("serviceUrl", serviceUrl);
      return this;
    }

    /** Specify the subscription name for this consumer. No default. */
    public Builder subscriptionName(String subscriptionName) {
      if (StringUtils.isNullOrEmpty(subscriptionName)) throw new NullPointerException("serviceUrl is null or empty");
      consumerProps.put("subscriptionName", subscriptionName);
      return this;
    }

    /**
     * Any properties set here will override the previous Pulsar client configuration.
     *
     * @param clientPropsMap Map<String, Object>
     * @return Builder
     * @see org.apache.pulsar.client.api.ClientBuilder#loadConf(Map)
     */
    public Builder clientProps(Map<String, Object> clientPropsMap) {
      if (clientPropsMap.isEmpty()) throw new NullPointerException("clientProps is empty");
      clientProps.putAll(clientPropsMap);
      return this;
    }

    /**
     * Any properties set here will override the previous Pulsar consumer configuration.
     *
     * @param consumerPropsMap Map<String, Object>
     * @return Builder
     * @see org.apache.pulsar.client.api.ConsumerBuilder#loadConf(Map)
     */
    public Builder consumerProps(Map<String, Object> consumerPropsMap) {
      if (consumerPropsMap.isEmpty()) throw new NullPointerException("consumerProps is empty");
      consumerProps.putAll(consumerPropsMap);
      return this;
    }
  }

  final Map<String, Object> clientProps, consumerProps;
  final String topic;
  final LazyPulsarInit lazyPulsarInit;

  PulsarCollector(Builder builder) {
    clientProps = builder.clientProps;
    consumerProps = builder.consumerProps;
    this.topic = builder.topic;
    this.lazyPulsarInit = new LazyPulsarInit(builder);
  }

  @Override
  public PulsarCollector start() {
    lazyPulsarInit.init();
    return this;
  }

  @Override public void close() throws IOException {
    lazyPulsarInit.close();
  }

  @Override public CheckResult check() {
    try {
      CheckResult failure = lazyPulsarInit.failure.get();
      if (failure != null) return failure;
      return CheckResult.OK;
    } catch (Throwable th) {
      Call.propagateIfFatal(th);
      return CheckResult.failed(th);
    }
  }

  @Override public String toString() {
    return "PulsarCollector{" +
        "clientProps=" + clientProps +
        ", consumerProps=" + consumerProps +
        ", topic=" + this.topic +
        "}";
  }
}
