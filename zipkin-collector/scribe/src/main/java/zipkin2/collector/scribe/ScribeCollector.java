/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.scribe;

import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.StorageComponent;

/**
 * This collector accepts Scribe logs in a specified category. Each log entry is expected to contain
 * a single span, which is TBinaryProtocol big-endian, then base64 encoded. These spans are chained
 * to an {@link SpanConsumer#accept asynchronous span consumer}.
 */
public final class ScribeCollector extends CollectorComponent {

  public static Builder newBuilder() {
    return new Builder();
  }

  /** Configuration including defaults needed to receive spans from a Scribe category. */
  public static final class Builder extends CollectorComponent.Builder {
    Collector.Builder delegate = Collector.newBuilder(ScribeCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    String category = "zipkin";
    int port = 9410;

    @Override public Builder storage(StorageComponent storage) {
      delegate.storage(storage);
      return this;
    }

    @Override public Builder metrics(CollectorMetrics metrics) {
      if (metrics == null) throw new NullPointerException("metrics == null");
      this.metrics = metrics.forTransport("scribe");
      delegate.metrics(this.metrics);
      return this;
    }

    @Override public Builder sampler(CollectorSampler sampler) {
      delegate.sampler(sampler);
      return this;
    }

    /** Category zipkin spans will be consumed from. Defaults to "zipkin" */
    public Builder category(String category) {
      if (category == null) throw new NullPointerException("category == null");
      this.category = category;
      return this;
    }

    /** The port to listen on. Defaults to 9410 */
    public Builder port(int port) {
      this.port = port;
      return this;
    }

    @Override public ScribeCollector build() {
      return new ScribeCollector(this);
    }
  }

  final NettyScribeServer server;

  ScribeCollector(Builder builder) {
    server = new NettyScribeServer(builder.port,
      new ScribeSpanConsumer(builder.delegate.build(), builder.metrics, builder.category));
  }

  /** Will throw an exception if the {@link Builder#port(int) port} is already in use. */
  @Override public ScribeCollector start() {
    server.start();
    return this;
  }

  @Override public CheckResult check() {
    if (!server.isRunning()) {
      return CheckResult.failed(new IllegalStateException("server not running"));
    }
    return CheckResult.OK;
  }

  /** Returns zero until {@link #start()} was called. */
  public int port() {
    return server.port();
  }

  @Override public final String toString() {
    return "ScribeCollector{port=" + port() + ", category=" + server.scribe.category + "}";
  }

  @Override public void close() {
    server.close();
  }
}
